package com.codeborne.selenide.impl;

import com.codeborne.selenide.Driver;
import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.ElementsContainer;
import com.codeborne.selenide.SelenideElement;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.FindBys;
import org.openqa.selenium.support.pagefactory.Annotations;
import org.openqa.selenium.support.pagefactory.DefaultElementLocatorFactory;
import org.openqa.selenium.support.pagefactory.DefaultFieldDecorator;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.List;

@ParametersAreNonnullByDefault
public class SelenideFieldDecorator extends DefaultFieldDecorator {
  private final SelenidePageFactory pageFactory;
  private final Driver driver;
  private final SearchContext searchContext;

  public SelenideFieldDecorator(SelenidePageFactory pageFactory, Driver driver, SearchContext searchContext) {
    super(new DefaultElementLocatorFactory(searchContext));
    this.pageFactory = pageFactory;
    this.driver = driver;
    this.searchContext = searchContext;
  }

  @Override
  @CheckReturnValue
  @Nullable
  public Object decorate(ClassLoader loader, Field field) {
    if (ElementsContainer.class.equals(field.getDeclaringClass()) && "self".equals(field.getName())) {
      return searchContext;
    }
    By selector = new Annotations(field).buildBy();
    if (WebElement.class.isAssignableFrom(field.getType())) {
      return ElementFinder.wrap(driver, searchContext, selector, 0);
    }
    if (ElementsCollection.class.isAssignableFrom(field.getType()) || isDecoratableList(field, WebElement.class)) {
      return new ElementsCollection(new BySelectorCollection(driver, searchContext, selector));
    }
    else if (ElementsContainer.class.isAssignableFrom(field.getType())) {
      return createElementsContainer(selector, field);
    }
    else if (isDecoratableList(field, ElementsContainer.class)) {
      return createElementsContainerList(field);
    }

    return super.decorate(loader, field);
  }

  @CheckReturnValue
  @Nonnull
  private List<ElementsContainer> createElementsContainerList(Field field) {
    List<ElementsContainer> result = new ArrayList<>();
    Class<?> listType = getListGenericType(field);
    if (listType == null) {
      throw new IllegalArgumentException("Cannot detect list type for " + field);
    }

    try {
      List<SelenideElement> selfList = SelenideElementListProxy.wrap(driver, factory.createLocator(field));
      for (SelenideElement element : selfList) {
        result.add(initElementsContainer(listType, element));
      }
      return result;
    } catch (Exception e) {
      throw new RuntimeException("Failed to create elements container list for field " + field.getName(), e);
    }
  }

  @CheckReturnValue
  @Nonnull
  private ElementsContainer createElementsContainer(By selector, Field field) {
    try {
      SelenideElement self = ElementFinder.wrap(driver, searchContext, selector, 0);
      return initElementsContainer(field.getType(), self);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create elements container for field " + field.getName(), e);
    }
  }

  @CheckReturnValue
  @Nonnull
  private ElementsContainer initElementsContainer(Class<?> type, SelenideElement self)
      throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
    Constructor<?> constructor = type.getDeclaredConstructor();
    constructor.setAccessible(true);
    ElementsContainer result = (ElementsContainer) constructor.newInstance();
    pageFactory.initElements(new SelenideFieldDecorator(pageFactory, driver, self), result);
    return result;
  }

  @CheckReturnValue
  private boolean isDecoratableList(Field field, Class<?> type) {
    if (!List.class.isAssignableFrom(field.getType())) {
      return false;
    }

    Class<?> listType = getListGenericType(field);

    return listType != null && type.isAssignableFrom(listType)
        && (field.getAnnotation(FindBy.class) != null || field.getAnnotation(FindBys.class) != null);
  }

  @CheckReturnValue
  @Nullable
  private Class<?> getListGenericType(Field field) {
    Type genericType = field.getGenericType();
    if (!(genericType instanceof ParameterizedType)) return null;

    Type[] actualTypeArguments = ((ParameterizedType) genericType).getActualTypeArguments();
    Type firstArgument = actualTypeArguments[0];
    if (firstArgument instanceof TypeVariable) {
      return (Class<?>) ((TypeVariable<?>) actualTypeArguments[0]).getGenericDeclaration();
    }
    return (Class<?>) firstArgument;
  }
}
