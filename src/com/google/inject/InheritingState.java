/**
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject;

import com.google.inject.internal.BindingImpl;
import com.google.inject.internal.Errors;
import com.google.inject.internal.Lists;
import com.google.inject.internal.Maps;
import com.google.inject.internal.MatcherAndConverter;
import static com.google.inject.internal.Preconditions.checkNotNull;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
class InheritingState implements State {

  private final State parent;

  // Must be a linked hashmap in order to preserve order of bindings in Modules.
  private final Map<Key<?>, Binding<?>> explicitBindingsMutable = Maps.newLinkedHashMap();
  private final Map<Key<?>, Binding<?>> explicitBindings
      = Collections.unmodifiableMap(explicitBindingsMutable);
  private final Map<Class<? extends Annotation>, Scope> scopes = Maps.newHashMap();
  private final List<MatcherAndConverter> converters = Lists.newArrayList();
  /*if[AOP]*/
  private final List<MethodAspect> methodAspects = Lists.newArrayList();
  /*end[AOP]*/
  private final WeakKeySet blacklistedKeys = new WeakKeySet();
  private final Object lock;

  InheritingState(State parent) {
    this.parent = checkNotNull(parent, "parent");
    this.lock = (parent == State.NONE) ? this : parent.lock();
  }

  public State parent() {
    return parent;
  }

  @SuppressWarnings("unchecked") // we only put in BindingImpls that match their key types
  public <T> BindingImpl<T> getExplicitBinding(Key<T> key) {
    Binding<?> binding = explicitBindings.get(key);
    return binding != null ? (BindingImpl<T>) binding : parent.getExplicitBinding(key);
  }

  public Map<Key<?>, Binding<?>> getExplicitBindingsThisLevel() {
    return explicitBindings;
  }

  public void putBinding(Key<?> key, BindingImpl<?> binding) {
    explicitBindingsMutable.put(key, binding);
  }

  public Scope getScope(Class<? extends Annotation> annotationType) {
    Scope scope = scopes.get(annotationType);
    return scope != null ? scope : parent.getScope(annotationType);
  }

  public void putAnnotation(Class<? extends Annotation> annotationType, Scope scope) {
    scopes.put(annotationType, scope);
  }

  public Iterable<MatcherAndConverter> getConvertersThisLevel() {
    return converters;
  }

  public void addConverter(MatcherAndConverter matcherAndConverter) {
    converters.add(matcherAndConverter);
  }

  public MatcherAndConverter getConverter(
      String stringValue, TypeLiteral<?> type, Errors errors, Object source) {
    MatcherAndConverter matchingConverter = null;
    for (State s = this; s != State.NONE; s = s.parent()) {
      for (MatcherAndConverter converter : s.getConvertersThisLevel()) {
        if (converter.getTypeMatcher().matches(type)) {
          if (matchingConverter != null) {
            errors.ambiguousTypeConversion(stringValue, source, type, matchingConverter, converter);
          }
          matchingConverter = converter;
        }
      }
    }
    return matchingConverter;
  }

  /*if[AOP]*/
  public void addMethodAspect(MethodAspect methodAspect) {
    methodAspects.add(methodAspect);
  }

  public List<MethodAspect> getMethodAspects() {
    List<MethodAspect> result = Lists.newArrayList();
    result.addAll(parent.getMethodAspects());
    result.addAll(methodAspects);
    return result;
  }
  /*end[AOP]*/

  public void blacklist(Key<?> key) {
    parent.blacklist(key);
    blacklistedKeys.add(key);
  }

  public boolean isBlacklisted(Key<?> key) {
    return blacklistedKeys.contains(key);
  }

  public Object lock() {
    return lock;
  }
}