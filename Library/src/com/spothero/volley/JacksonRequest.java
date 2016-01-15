/*
 * Copyright (C) 2013 SpotHero
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.spothero.volley;

import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.http.HttpStatus;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This extension uses the Jackson library to serialize and deserialize request and response content.
 * This implementation behaves as follows:
 *     - GET request parameters are passed through the URL, with no body content.
 *     - POST/PUT request bodies have JSON content.
 *     - The same {@link com.fasterxml.jackson.databind.ObjectMapper} is used for serialization and deserialization.
 *
 * @param <T> The generic type used to parse response content.
 */
public class JacksonRequest<T> extends Request<T> {

	private static final int DEFAULT_TIMEOUT = 30000; // 30 seconds
	private static ObjectMapper OBJECT_MAPPER;

	private Map<String, String> mParams;
    private byte[] mBody;
	private List<Integer> mAcceptedStatusCodes;
	protected final JacksonRequestListener<T> mListener;
    protected final JacksonTypeProvider mTypeProvider;

	public JacksonRequest(int method, String url, JacksonRequestListener<T> listener, JacksonTypeProvider typeProvider) {
		this(DEFAULT_TIMEOUT, method, url, null, null, listener, typeProvider);
	}

	public JacksonRequest(int timeout, int method, String url, JacksonRequestListener<T> listener, JacksonTypeProvider typeProvider) {
		this(timeout, method, url, null, null, listener, typeProvider);
	}

	public JacksonRequest(int method, String url, Map<String, String> params, JacksonRequestListener<T> listener, JacksonTypeProvider typeProvider) {
		this(DEFAULT_TIMEOUT, method, url, params, null, listener, typeProvider);
	}

    public JacksonRequest(int timeout, int method, String url, Map<String, String> params, JacksonRequestListener<T> listener, JacksonTypeProvider typeProvider) {
        this(timeout, method, url, params, null, listener, typeProvider);
    }

    public JacksonRequest(int method, String url, Object entity, JacksonRequestListener<T> listener, JacksonTypeProvider typeProvider) {
        this(DEFAULT_TIMEOUT, method, url, null, entity, listener, typeProvider);
    }

    public JacksonRequest(int timeout, int method, String url, T entity, JacksonRequestListener<T> listener, JacksonTypeProvider typeProvider) {
        this(timeout, method, url, null, entity, listener, typeProvider);
    }

	protected JacksonRequest(int timeout, int method, String url, Map<String, String> params, Object entity, JacksonRequestListener<T> listener, JacksonTypeProvider typeProvider) {
		super(method, toUrl(method, url, params), null);

		setShouldCache(false);
		mListener = listener;
        mTypeProvider = typeProvider;
        applyObjectMapperConfiguration(getObjectMapper());

        System.out.println("Entity is: " + entity);
        if (method == Method.GET) {
            mParams = params;
        } else if ((method == Method.PUT || method == Method.POST) && entity != null) {
            try {
                mBody = serializeEntity(entity);
                System.out.println("Serialized body is: " + new String(mBody));
            } catch (JsonProcessingException e) {
                VolleyLog.e(e, "An error occurred while serializing the request body");
                System.out.println("An error occurred while serializing the request body");
                e.printStackTrace();
            }
        }

		mAcceptedStatusCodes = new ArrayList<Integer>();
		mAcceptedStatusCodes.add(HttpStatus.SC_OK);
		mAcceptedStatusCodes.add(HttpStatus.SC_CREATED);
		mAcceptedStatusCodes.add(HttpStatus.SC_ACCEPTED);
        mAcceptedStatusCodes.add(HttpStatus.SC_NO_CONTENT);
        mAcceptedStatusCodes.add(HttpStatus.SC_PARTIAL_CONTENT);

		setRetryPolicy(new DefaultRetryPolicy(timeout, 1, 1));
	}

	/**
	 * Gets the ObjectMapper singleton.
	 *
	 * @return The ObjectMapper instance used when mapping network requests and responses
	 */
	public static ObjectMapper getObjectMapper() {
        if (OBJECT_MAPPER == null) {
            OBJECT_MAPPER = new ObjectMapper();
        }
		return OBJECT_MAPPER;
	}

    /**
     * This method is executed upon instantiation and should be used to apply configuration to the mapper.
     * It does not need to be called manually.
     *
     * @param mapper The ObjectMapper object returned by {@Link #getObjectMapper()}.
     */
    protected void applyObjectMapperConfiguration(ObjectMapper mapper) {  }

	/**
	 * Allows you to add additional status codes (besides 2xx) that will be parsed.
	 *
	 * @param statusCodes An array of additional status codes to parse network responses for
	 */
	public void addAcceptedStatusCodes(int[] statusCodes) {
		for (int statusCode : statusCodes) {
			mAcceptedStatusCodes.add(statusCode);
		}
	}

	/**
	 * Gets all status codes that will be parsed as successful (Note: some {@link com.android.volley.toolbox.HttpStack}
	 * implementations, including the default, may not allow certain status codes to be parsed. To get around this
	 * limitation, use a custom HttpStack, such as the one provided with the excellent OkHttp library
	 *
	 * @return A list of all status codes that will be counted as successful
	 */
	public List<Integer> getAcceptedStatusCodes() {
		return mAcceptedStatusCodes;
	}

	/**
	 * Converts a base URL and parameters into a full URL
     *
     * @param method The {@link com.android.volley.Request.Method} of the URL
     * @param baseUrl The base URL
     * @param params The parameters to be appended to the URL if a GET method is used
     *
	 * @return The full URL
	 */
	private static String toUrl(int method, String baseUrl, Map<String, String> params) {
		StringBuilder result = new StringBuilder();
		if (method == Method.GET && params != null && !params.isEmpty()) {
            try {
				for (Map.Entry<String, String> e : params.entrySet()) {
					result.append(result.length() == 0 ? "?" : "&");
					result.append(URLEncoder.encode(e.getKey(), "UTF-8"));
					result.append("=");
					result.append(URLEncoder.encode(
							e.getValue() == null || e.getValue().equals("null") ? "" : e.getValue(),
							"UTF-8")
					);
				}
			} catch (UnsupportedEncodingException e) {
				// Ignore
			}
		}
		return result.insert(0, baseUrl).toString();
	}

    protected byte[] serializeEntity(Object entity) throws JsonProcessingException {
        return getObjectMapper().writeValueAsBytes(entity);
    }

	@Override
	protected void deliverResponse(T response) {
		mListener.onResponse(response, HttpStatus.SC_OK, null);
        mListener.onSuccess(response);
	}

	@Override
	public void deliverError(VolleyError error) {
		int statusCode;
		if (error != null && error.networkResponse != null) {
			statusCode = error.networkResponse.statusCode;
		} else {
			statusCode = 0;
		}

		mListener.onResponse(null, statusCode, error);
        mListener.onError(error, statusCode);
	}

	@Override
	protected Response<T> parseNetworkResponse(NetworkResponse response) {
		JavaType returnType = mTypeProvider.getReturnType();
		T returnData = null;
		if (returnType != null) {
			try {
				if (response.data != null) {
					returnData = getObjectMapper().readValue(response.data, returnType);
				} else if (response instanceof JacksonNetworkResponse) {
					returnData = getObjectMapper().readValue(((JacksonNetworkResponse)response).inputStream, returnType);
				}
			} catch (Exception e) {
				VolleyLog.e(e, "An error occurred while parsing network response:");
				return Response.error(new ParseError(response));
			}
		}
		return mListener.onParseResponseComplete(Response.success(returnData, HttpHeaderParser.parseCacheHeaders(response)));
	}

	@Override
	public Map<String, String> getHeaders() throws AuthFailureError {
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("Accept", "application/json");

		/*if (getMethod() == Method.POST || getMethod() == Method.PUT) {
			headers.put("Content-Type", "application/json");
		}*/

		return headers;
	}

    /**
     * Returns query parameters for GET requests.
     *
     * @return GET parameters
     */
	@Override
	public Map<String, String> getParams() {
		return mParams;
	}

    /**
     * Returns request body for PUT and POST requests.
     *
     * @return
     * @throws AuthFailureError
     */
    @Override
    public byte[] getBody() throws AuthFailureError { return mBody; }

    @Override
    public String getBodyContentType() {
        return getMethod() == Method.POST || getMethod() == Method.PUT ? "application/json" : null;
    }
}
