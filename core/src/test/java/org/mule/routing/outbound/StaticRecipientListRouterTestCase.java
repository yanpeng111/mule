/*
 * $Id:StaticRecipientListRouterTestCase.java 5937 2007-04-09 22:35:04Z rossmason $
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.routing.outbound;

import org.mule.DefaultMuleMessage;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.MuleSession;
import org.mule.api.endpoint.OutboundEndpoint;
import org.mule.tck.AbstractMuleTestCase;
import org.mule.tck.MuleTestUtils;

import com.mockobjects.dynamic.Mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StaticRecipientListRouterTestCase extends AbstractMuleTestCase
{
    public StaticRecipientListRouterTestCase()
    {
        setStartContext(true);        
    }

    public void testRecipientListRouterAsync() throws Exception
    {
        Mock session = MuleTestUtils.getMockSession();
        session.matchAndReturn("getFlowConstruct", getTestService());
        
        OutboundEndpoint endpoint1 = getTestOutboundEndpoint("Test1Provider");
        assertNotNull(endpoint1);
        Mock mockendpoint1 = RouterTestUtils.getMockEndpoint(endpoint1);

        List<String> recipients = new ArrayList<String>();
        String recipient1 = "test://recipient1";
        recipients.add(recipient1);
        String recipient2 = "test://recipient2";
        recipients.add(recipient2);
        MockingStaticRecipientList router = createObject(MockingStaticRecipientList.class);

        router.setRecipients(recipients);

        List<OutboundEndpoint> endpoints = new ArrayList<OutboundEndpoint>();
        endpoints.add((OutboundEndpoint) mockendpoint1.proxy());
        router.setEndpoints(endpoints);
        router.setMuleContext(muleContext);

        assertEquals(2, router.getRecipients().size());

        MuleMessage message = new DefaultMuleMessage("test event", muleContext);
        assertTrue(router.isMatch(message));

       // Set up the mock endpoints as we discover them
        final List<Mock> mockEndpoints = new ArrayList<Mock>();
        router.setMockEndpointListener(new MockEndpointListener()
        {
            public void mockEndpointAdded(Mock recipient)
            {
                mockEndpoints.add(recipient);
                recipient.expect("process", RouterTestUtils.getArgListCheckerMuleEvent());
            }
        });

        router.route(new OutboundRoutingTestEvent(message, (MuleSession)session.proxy()));
        for (Mock mockEp : mockEndpoints)
        {
            mockEp.verify();
        }
    }


    public void testRecipientListRouterSync() throws Exception
    {
        Mock session = MuleTestUtils.getMockSession();
        session.matchAndReturn("getFlowConstruct", getTestService());
        session.matchAndReturn("setFlowConstruct", RouterTestUtils.getArgListCheckerFlowConstruct(), null);
        
        OutboundEndpoint endpoint1 = getTestOutboundEndpoint("Test1Provider");
        assertNotNull(endpoint1);

        List<String> recipients = new ArrayList<String>();
        recipients.add("test://recipient1?exchange-pattern=request-response");
        recipients.add("test://recipient2?exchange-pattern=request-response");
        MockingStaticRecipientList router = createObject(MockingStaticRecipientList.class);

        router.setRecipients(recipients);

        List<OutboundEndpoint> endpoints = new ArrayList<OutboundEndpoint>();
        endpoints.add(endpoint1);
        router.setEndpoints(endpoints);
        router.setMuleContext(muleContext);

        assertEquals(2, router.getRecipients().size());

        MuleMessage message = new DefaultMuleMessage("test event", muleContext);
        assertTrue(router.isMatch(message));
        // note this router clones endpoints so that the endpointUri can be
        // changed

        // The static recipient list router duplicates the message for each endpoint
        // so we can't
        // check for equality on the arguments passed to the dispatch / send methods
        message = new DefaultMuleMessage("test event", muleContext);
        final MuleEvent event = new OutboundRoutingTestEvent(message, null);

        // Set up the mock endpoints as we discover them
         final List<Mock> mockEndpoints = new ArrayList<Mock>();
         router.setMockEndpointListener(new MockEndpointListener()
         {
             public void mockEndpointAdded(Mock recipient)
             {
                 mockEndpoints.add(recipient);
                 recipient.expectAndReturn("process", RouterTestUtils.getArgListCheckerMuleEvent(), event);
             }
         });

        router.getRecipients().add("test://recipient3?exchange-pattern=request-response");
        MuleEvent result = router.route(new OutboundRoutingTestEvent(message, (MuleSession)session.proxy()));
        assertNotNull(result);
        MuleMessage resultMessage = result.getMessage();
        assertNotNull(resultMessage);
        assertTrue(resultMessage.getPayload() instanceof List);
        assertEquals(3, ((List)resultMessage.getPayload()).size());
        session.verify();

    }

    public void testBadRecipientListRouter() throws Exception
    {
        Mock session = MuleTestUtils.getMockSession();

        OutboundEndpoint endpoint1 = getTestOutboundEndpoint("Test1Provider");
        assertNotNull(endpoint1);

        List<String> recipients = new ArrayList<String>();
        recipients.add("malformed-endpointUri-recipient1");
        StaticRecipientList router = createObject(StaticRecipientList.class);

        router.setRecipients(recipients);

        List<OutboundEndpoint> endpoints = new ArrayList<OutboundEndpoint>();
        endpoints.add(endpoint1);
        router.setEndpoints(endpoints);

        assertEquals(1, router.getRecipients().size());

        MuleMessage message = new DefaultMuleMessage("test event", muleContext);
        assertTrue(router.isMatch(message));
        try
        {
            router.route(new OutboundRoutingTestEvent(message, (MuleSession)session.proxy()));
            fail("Should not allow malformed endpointUri");
        }
        catch (Exception e)
        {
            // ignore
        }
        session.verify();
    }

    /** subclass the router, so that we can mock the endpoints it creates dynamically.  */
    public static class MockingStaticRecipientList extends StaticRecipientList
    {
        private Map<String, Mock> recipients = new HashMap<String, Mock>();
        private MockEndpointListener listener;

        Mock getRecipient(String name)
        {
            return recipients.get(name);
        }

        public void setMockEndpointListener(MockEndpointListener listener)
        {
            this.listener = listener;
        }

        @Override
        protected OutboundEndpoint getRecipientEndpointFromString(MuleMessage message, String recipient) throws MuleException
        {
            OutboundEndpoint endpoint = super.getRecipientEndpointFromString(message, recipient);
            if (!recipients.containsKey(recipient))
            {
                Mock mock = RouterTestUtils.getMockEndpoint(endpoint);
                recipients.put(recipient, mock);
                if (listener != null)
                    listener.mockEndpointAdded(mock);
            }
            return (OutboundEndpoint) recipients.get(recipient).proxy();
        }
    }

    /** Callback called when new recipient is added */
    interface MockEndpointListener
    {
        void mockEndpointAdded(Mock recipient);
    }
}
