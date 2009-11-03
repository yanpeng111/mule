/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.component;

import org.mule.api.MuleException;
import org.mule.api.component.Component;
import org.mule.api.component.LifecycleAdapter;
import org.mule.api.lifecycle.InitialisationCallback;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.model.EntryPointResolverSet;
import org.mule.api.object.ObjectFactory;
import org.mule.api.routing.BindingCollection;
import org.mule.config.PoolingProfile;
import org.mule.util.pool.DefaultLifecycleEnabledObjectPool;
import org.mule.util.pool.LifecyleEnabledObjectPool;
import org.mule.util.pool.ObjectPool;

/**
 * <code>PooledJavaComponent</code> implements pooling.
 */
public class PooledJavaComponent extends AbstractJavaComponent
{

    protected PoolingProfile poolingProfile;
    protected LifecyleEnabledObjectPool lifecycleAdapterPool;

    public PooledJavaComponent()
    {
        super();
    }

    public PooledJavaComponent(ObjectFactory objectFactory)
    {
        this(objectFactory, null);
    }

    public PooledJavaComponent(ObjectFactory objectFactory, PoolingProfile poolingProfile)
    {
        super(objectFactory);
        this.poolingProfile = poolingProfile;
    }

    public PooledJavaComponent(ObjectFactory objectFactory,
                               PoolingProfile poolingProfile,
                               EntryPointResolverSet entryPointResolverSet,
                               BindingCollection bindingCollection)
    {
        super(objectFactory, entryPointResolverSet, bindingCollection);
        this.poolingProfile = poolingProfile;
    }

    protected LifecycleAdapter borrowComponentLifecycleAdaptor() throws Exception
    {
        return (LifecycleAdapter) lifecycleAdapterPool.borrowObject();
    }

    protected void returnComponentLifecycleAdaptor(LifecycleAdapter lifecycleAdapter)
    {
        lifecycleAdapterPool.returnObject(lifecycleAdapter);
    }

    protected void doStart() throws MuleException
    {
        super.doStart();
        // Wrap pool's objectFactory with a LifeCycleAdaptor factory so we pool
        // LifeCycleAdaptor's and not just pojo instances.
        lifecycleAdapterPool = new DefaultLifecycleEnabledObjectPool(new LifeCycleAdaptorFactory(), poolingProfile);
        lifecycleAdapterPool.initialise();
        lifecycleAdapterPool.start();
    }

    protected void doStop() throws MuleException
    {
        super.doStop();
        if (lifecycleAdapterPool != null)
        {
            lifecycleAdapterPool.stop();
            lifecycleAdapterPool.close();
            lifecycleAdapterPool = null;
        }
    }

    public void setPoolingProfile(PoolingProfile poolingProfile)
    {
        // TODO What happens if this is set while component is started? Should we i)
        // do nothing ii) issue warning iii) stop/start the pool
        // (!!) iv) throw exception?
        this.poolingProfile = poolingProfile;
    }

    public PoolingProfile getPoolingProfile()
    {
        return poolingProfile;
    }

    /**
     * <code>LifeCycleAdaptorFactory</code> wraps the Component' s
     * {@link ObjectFactory}. The LifeCycleAdaptorFactory <code>getInstance()</code> method
     * creates a new {@link LifecycleAdapter} wrapping the object instance obtained
     * for the component instance {@link ObjectFactory} set on the {@link Component}.
     * <br/>
     * This allows us to keep {@link LifecycleAdapter} creation in the Component and
     * out of the {@link DefaultLifecycleEnabledObjectPool} and to use the generic
     * {@link ObjectPool} interface.
     */
    protected class LifeCycleAdaptorFactory implements ObjectFactory
    {

        public Object getInstance() throws Exception
        {
            return createLifeCycleAdaptor();
        }

        public Class<?> getObjectClass()
        {
            return LifecycleAdapter.class;
        }

        public void initialise() throws InitialisationException
        {
            objectFactory.initialise();
        }

        public void dispose()
        {
            objectFactory.dispose();
        }

        public void addObjectInitialisationCallback(InitialisationCallback callback)
        {
            objectFactory.addObjectInitialisationCallback(callback);
        }

        public boolean isSingleton()
        {
            return false;
        }

        public boolean isExternallyManagedLifecycle()
        {
            return objectFactory.isExternallyManagedLifecycle();
        }

        public boolean isAutoWireObject()
        {
            return objectFactory.isAutoWireObject();
        }
    }

}
