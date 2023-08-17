/*
 * Copyright 2002-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.reactive.result.view;

import java.util.Locale;
import java.util.function.Function;

import reactor.core.publisher.Mono;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.PatternMatchUtils;

/**
 * A {@link ViewResolver} that allows direct resolution of symbolic view names
 * to URLs without explicit mapping definitions. This is useful if symbolic names
 * match the names of view resources in a straightforward manner (i.e. the
 * symbolic name is the unique part of the resource's filename), without the need
 * for a dedicated mapping to be defined for each view.
 *
 * <p>Supports {@link AbstractUrlBasedView} subclasses like
 * {@link org.springframework.web.reactive.result.view.freemarker.FreeMarkerView}.
 * The view class for all views generated by this resolver can be specified
 * via the "viewClass" property.
 *
 * <p>View names can either be resource URLs themselves, or get augmented by a
 * specified prefix and/or suffix. Exporting an attribute that holds the
 * RequestContext to all views is explicitly supported.
 *
 * <p>Example: prefix="templates/", suffix=".ftl", viewname="test" &rarr;
 * "templates/test.ftl"
 *
 * <p>As a special feature, redirect URLs can be specified via the "redirect:"
 * prefix. E.g.: "redirect:myAction" will trigger a redirect to the given
 * URL, rather than resolution as standard view name. This is typically used
 * for redirecting to a controller URL after finishing a form workflow.
 *
 * <p>Note: This class does not support localized resolution, i.e. resolving
 * a symbolic view name to different resources depending on the current locale.
 *
 * @author Rossen Stoyanchev
 * @author Sebastien Deleuze
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 5.0
 */
public class UrlBasedViewResolver extends ViewResolverSupport
		implements ViewResolver, ApplicationContextAware, InitializingBean {

	/**
	 * Prefix for special view names that specify a redirect URL (usually
	 * to a controller after a form has been submitted and processed).
	 * Such view names will not be resolved in the configured default
	 * way but rather be treated as special shortcut.
	 */
	public static final String REDIRECT_URL_PREFIX = "redirect:";


	@Nullable
	private Class<?> viewClass;

	private String prefix = "";

	private String suffix = "";

	@Nullable
	private String[] viewNames;

	private Function<String, RedirectView> redirectViewProvider = RedirectView::new;

	@Nullable
	private String requestContextAttribute;

	@Nullable
	private ApplicationContext applicationContext;


	/**
	 * Set the view class that should be used to create views.
	 * @param viewClass a class that is assignable to the required view class
	 * (by default: AbstractUrlBasedView)
	 * @see #requiredViewClass()
	 * @see #instantiateView()
	 * @see AbstractUrlBasedView
	 */
	public void setViewClass(@Nullable Class<?> viewClass) {
		if (viewClass != null && !requiredViewClass().isAssignableFrom(viewClass)) {
			String name = viewClass.getName();
			throw new IllegalArgumentException("Given view class [" + name + "] " +
					"is not of type [" + requiredViewClass().getName() + "]");
		}
		this.viewClass = viewClass;
	}

	/**
	 * Return the view class to be used to create views.
	 * @see #setViewClass
	 */
	@Nullable
	protected Class<?> getViewClass() {
		return this.viewClass;
	}

	/**
	 * Set the prefix that gets prepended to view names when building a URL.
	 */
	public void setPrefix(@Nullable String prefix) {
		this.prefix = (prefix != null ? prefix : "");
	}

	/**
	 * Return the prefix that gets prepended to view names when building a URL.
	 */
	protected String getPrefix() {
		return this.prefix;
	}

	/**
	 * Set the suffix that gets appended to view names when building a URL.
	 */
	public void setSuffix(@Nullable String suffix) {
		this.suffix = (suffix != null ? suffix : "");
	}

	/**
	 * Return the suffix that gets appended to view names when building a URL.
	 */
	protected String getSuffix() {
		return this.suffix;
	}

	/**
	 * Set the view names (or name patterns) that can be handled by this
	 * {@link ViewResolver}. View names can contain simple wildcards such that
	 * 'my*', '*Report' and '*Repo*' will all match the view name 'myReport'.
	 * @see #canHandle
	 */
	public void setViewNames(@Nullable String... viewNames) {
		this.viewNames = viewNames;
	}

	/**
	 * Return the view names (or name patterns) that can be handled by this
	 * {@link ViewResolver}.
	 */
	@Nullable
	protected String[] getViewNames() {
		return this.viewNames;
	}

	/**
	 * URL based {@link RedirectView} provider which can be used to provide, for example,
	 * redirect views with a custom default status code.
	 */
	public void setRedirectViewProvider(Function<String, RedirectView> redirectViewProvider) {
		this.redirectViewProvider = redirectViewProvider;
	}

	/**
	 * Set the name of the {@link RequestContext} attribute for all views.
	 * @param requestContextAttribute name of the RequestContext attribute
	 * @see AbstractView#setRequestContextAttribute
	 */
	public void setRequestContextAttribute(@Nullable String requestContextAttribute) {
		this.requestContextAttribute = requestContextAttribute;
	}

	/**
	 * Return the name of the {@link RequestContext} attribute for all views, if any.
	 */
	@Nullable
	protected String getRequestContextAttribute() {
		return this.requestContextAttribute;
	}

	/**
	 * Accept the containing {@code ApplicationContext}, if any.
	 * <p>To be used for the initialization of newly created {@link View} instances,
	 * applying lifecycle callbacks and providing access to the containing environment.
	 * @see #setViewClass
	 * @see #createView
	 * @see #applyLifecycleMethods
	 */
	@Override
	public void setApplicationContext(@Nullable ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	/**
	 * Return the containing {@code ApplicationContext}, if any.
	 * @see #setApplicationContext
	 */
	@Nullable
	public ApplicationContext getApplicationContext() {
		return this.applicationContext;
	}


	@Override
	public void afterPropertiesSet() throws Exception {
		if (getViewClass() == null) {
			throw new IllegalArgumentException("Property 'viewClass' is required");
		}
	}


	@Override
	public Mono<View> resolveViewName(String viewName, Locale locale) {
		if (!canHandle(viewName, locale)) {
			return Mono.empty();
		}

		AbstractUrlBasedView urlBasedView;
		if (viewName.startsWith(REDIRECT_URL_PREFIX)) {
			String redirectUrl = viewName.substring(REDIRECT_URL_PREFIX.length());
			urlBasedView = this.redirectViewProvider.apply(redirectUrl);
		}
		else {
			urlBasedView = createView(viewName);
		}

		View view = applyLifecycleMethods(viewName, urlBasedView);
		return urlBasedView.resourceExists(locale)
				.flatMap(exists -> exists ? Mono.just(view) : Mono.empty());
	}

	/**
	 * Indicates whether this {@link ViewResolver} can handle the supplied
	 * view name. If not, an empty result is returned. The default implementation
	 * checks against the configured {@link #setViewNames view names}.
	 * @param viewName the name of the view to retrieve
	 * @param locale the Locale to retrieve the view for
	 * @return whether this resolver applies to the specified view
	 * @see org.springframework.util.PatternMatchUtils#simpleMatch(String, String)
	 */
	protected boolean canHandle(String viewName, Locale locale) {
		String[] viewNames = getViewNames();
		return (viewNames == null || PatternMatchUtils.simpleMatch(viewNames, viewName));
	}

	/**
	 * Return the required type of view for this resolver.
	 * This implementation returns {@link AbstractUrlBasedView}.
	 * @see #instantiateView()
	 * @see AbstractUrlBasedView
	 */
	protected Class<?> requiredViewClass() {
		return AbstractUrlBasedView.class;
	}

	/**
	 * Instantiate the specified view class.
	 * <p>The default implementation uses reflection to instantiate the class.
	 * @return a new instance of the view class
	 * @since 5.3
	 * @see #setViewClass
	 */
	protected AbstractUrlBasedView instantiateView() {
		Class<?> viewClass = getViewClass();
		Assert.state(viewClass != null, "No view class");
		return (AbstractUrlBasedView) BeanUtils.instantiateClass(viewClass);
	}

	/**
	 * Creates a new View instance of the specified view class and configures it.
	 * <p>Does <i>not</i> perform any lookup for pre-defined View instances.
	 * <p>Spring lifecycle methods as defined by the bean container do not have to
	 * be called here: They will be automatically applied afterwards, provided
	 * that an {@link #setApplicationContext ApplicationContext} is available.
	 * @param viewName the name of the view to build
	 * @return the View instance
	 * @see #getViewClass()
	 * @see #applyLifecycleMethods
	 */
	protected AbstractUrlBasedView createView(String viewName) {
		AbstractUrlBasedView view = instantiateView();
		view.setSupportedMediaTypes(getSupportedMediaTypes());
		view.setDefaultCharset(getDefaultCharset());
		view.setUrl(getPrefix() + viewName + getSuffix());

		String requestContextAttribute = getRequestContextAttribute();
		if (requestContextAttribute != null) {
			view.setRequestContextAttribute(requestContextAttribute);
		}

		return view;
	}

	/**
	 * Apply the containing {@link ApplicationContext}'s lifecycle methods
	 * to the given {@link View} instance, if such a context is available.
	 * @param viewName the name of the view
	 * @param view the freshly created View instance, pre-configured with
	 * {@link AbstractUrlBasedView}'s properties
	 * @return the {@link View} instance to use (either the original one
	 * or a decorated variant)
	 * @see #getApplicationContext()
	 * @see ApplicationContext#getAutowireCapableBeanFactory()
	 * @see org.springframework.beans.factory.config.AutowireCapableBeanFactory#initializeBean
	 */
	protected View applyLifecycleMethods(String viewName, AbstractUrlBasedView view) {
		ApplicationContext context = getApplicationContext();
		if (context != null) {
			Object initialized = context.getAutowireCapableBeanFactory().initializeBean(view, viewName);
			if (initialized instanceof View initializedView) {
				return initializedView;
			}
		}
		return view;
	}

}
