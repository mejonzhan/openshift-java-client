/******************************************************************************* 
 * Copyright (c) 2012 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Red Hat, Inc. - initial API and implementation 
 ******************************************************************************/
package com.openshift.internal.client;

import static com.openshift.client.utils.MockUtils.anyForm;
import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;

import com.openshift.client.HttpMethod;
import com.openshift.client.IHttpClient;
import com.openshift.client.OpenShiftEndpointException;
import com.openshift.client.OpenShiftException;
import com.openshift.client.utils.MessageAssert;
import com.openshift.client.utils.OpenShiftTestConfiguration;
import com.openshift.client.utils.Samples;
import com.openshift.internal.client.httpclient.HttpClientException;
import com.openshift.internal.client.httpclient.NotFoundException;
import com.openshift.internal.client.response.Link;
import com.openshift.internal.client.response.LinkParameter;
import com.openshift.internal.client.response.LinkParameterType;
import com.openshift.internal.client.response.Message;
import com.openshift.internal.client.response.Message.Severity;
import com.openshift.internal.client.response.RestResponse;

/**
 * @author Andre Dietisheim
 */
public class RestServiceTest {

	private IRestService service;
	private IHttpClient clientMock;

	@Before
	public void setUp() throws FileNotFoundException, IOException, OpenShiftException, HttpClientException {
		this.clientMock = mock(IHttpClient.class);
		String jsonResponse = "{}";
		when(clientMock.get(any(URL.class))).thenReturn(jsonResponse);
		when(clientMock.post(anyForm(), any(URL.class))).thenReturn(jsonResponse);
		when(clientMock.put(anyForm(), any(URL.class))).thenReturn(jsonResponse);
		when(clientMock.delete(anyForm(), any(URL.class))).thenReturn(jsonResponse);

		OpenShiftTestConfiguration configuration = new OpenShiftTestConfiguration();

		this.service = new RestService(
				configuration.getStagingServer(),
				configuration.getClientId(),
				clientMock);
	}

	@Test(expected = OpenShiftException.class)
	public void throwsIfRequiredParameterMissing() throws OpenShiftException, SocketTimeoutException {
		// operation
		LinkParameter parameter =
				new LinkParameter("required string parameter", LinkParameterType.STRING, null, null, null);
		Link link = new Link("1 required parameter", "/dummy", HttpMethod.GET, Arrays.asList(parameter), null);
		service.request(link, new HashMap<String, Object>());
	}

	@Test
	public void shouldNotThrowIfNoReqiredParameter() throws OpenShiftException, SocketTimeoutException {
		// operation
		Link link = new Link("0 required parameter", "/dummy", HttpMethod.GET, null, null);
		service.request(link, new HashMap<String, Object>());
	}

	@Test
	public void shouldGetIfGetHttpMethod() throws OpenShiftException, SocketTimeoutException, HttpClientException {
		// operation
		service.request(new Link("0 required parameter", "http://www.redhat.com", HttpMethod.GET, null, null));
		// verifications
		verify(clientMock, times(1)).get(any(URL.class));
	}

	@Test
	public void shouldPostIfPostHttpMethod() throws OpenShiftException, SocketTimeoutException, HttpClientException,
			UnsupportedEncodingException {
		// operation
		service.request(new Link("0 required parameter", "http://www.redhat.com", HttpMethod.POST, null, null));
		// verifications
		verify(clientMock, times(1)).post(anyForm(), any(URL.class));
	}

	@Test
	public void shouldPutIfPutHttpMethod() throws OpenShiftException, SocketTimeoutException, HttpClientException,
			UnsupportedEncodingException {
		// operation
		service.request(new Link("0 required parameter", "http://www.redhat.com", HttpMethod.PUT, null, null));
		// verifications
		verify(clientMock, times(1)).put(anyForm(), any(URL.class));
	}

	@Test
	public void shouldDeleteIfDeleteHttpMethod() throws OpenShiftException, SocketTimeoutException, HttpClientException, UnsupportedEncodingException {
		// operation
		service.request(new Link("0 required parameter", "http://www.redhat.com", HttpMethod.DELETE, null, null));
		// verifications
		verify(clientMock, times(1)).delete(anyForm(), any(URL.class));
	}

	@Test
	public void shouldNotAddServerToAbsUrl() throws OpenShiftException, SocketTimeoutException, HttpClientException,
			MalformedURLException {
		// operation
		String url = "http://www.redhat.com";
		service.request(new Link("0 required parameter", url, HttpMethod.GET, null, null));
		// verifications
		verify(clientMock, times(1)).get(new URL(url));

	}

	@Test
	public void shouldAddServerToPath() throws OpenShiftException, SocketTimeoutException, HttpClientException,
			MalformedURLException {
		// operation
		String url = "/adietisheim-redhat";
		service.request(new Link("0 require parameter", url, HttpMethod.GET, null, null));
		// verifications
		String targetUrl = service.getServiceUrl() + url.substring(1, url.length());
		verify(clientMock, times(1)).get(new URL(targetUrl));
	}

	@Test
	public void shouldNotAddBrokerPathIfPresent() throws OpenShiftException, SocketTimeoutException,
			HttpClientException, MalformedURLException {
		// operation
		String url = "/broker/rest/adietisheim-redhat";
		service.request(new Link("0 require parameter", url, HttpMethod.GET, null, null));
		// verifications
		String targetUrl = service.getPlatformUrl() + url;
		verify(clientMock, times(1)).get(new URL(targetUrl));
	}

	@Test
	public void shouldThrowExceptionWithResponseOnNotFound() throws Throwable {
		try {
			// pre-conditions
			NotFoundException e = new NotFoundException(Samples.GET_DOMAIN_NOTFOUND_JSON.getContentAsString());
			when(clientMock.get(any(URL.class))).thenThrow(e);
			// operation
			service.request(new Link("0 require parameter", "/broker/rest/adietisheim", HttpMethod.GET, null, null));
			// verifications
			fail("OpenShiftEndPointException expected, did not occurr");
		} catch (OpenShiftEndpointException e) {
			assertThat(e.getRestResponse()).isNotNull();
		}
	}

	@Test
	public void shouldGetMessageIfErrors() throws Throwable {
		try {
			// pre-conditions
			when(clientMock.post(anyForm(), any(URL.class)))
					.thenThrow(new HttpClientException(Samples.POST_DOMAINS_NEWDOMAIN_KO.getContentAsString()));
			// operation
			service.request(new Link("0 require parameter", "/broker/rest/domains", HttpMethod.POST, null, null));
			// verifications
			fail("OpenShiftEndPointException expected, did not occurr");
		} catch (OpenShiftEndpointException e) {
			RestResponse restResponse = e.getRestResponse();
			assertThat(restResponse).isNotNull();
			assertThat(restResponse.getMessages()).hasSize(1);
			Message message = restResponse.getMessages().get(0);
			assertThat(message).isNotNull();
			assertThat(new MessageAssert(message))
					.hasText("User already has a domain associated. Update the domain to modify.")
					.hasSeverity(Severity.ERROR)
					.hasExitCode(102)
					.hasParameter(null);
		}
	}
}
