/*
 * _=_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=
 * Repose
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
 * Copyright (C) 2010 - 2015 Rackspace US, Inc.
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
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
 * =_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=_
 */
package org.openrepose.powerfilter.intrafilterLogging;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.openrepose.commons.utils.servlet.http.MutableHttpServletRequest;
import org.openrepose.core.systemmodel.Filter;
import org.openrepose.powerfilter.filtercontext.FilterContext;

import javax.servlet.ServletInputStream;
import java.io.IOException;
import java.util.*;

/**
 * POJO that is used to log details about a response when the log level is set to TRACE.
 * See {@link org.openrepose.powerfilter.PowerFilterChain#intrafilterRequestLog} for more details.
 */
public class RequestLog {

    String preamble;
    String timestamp;
    String currentFilter;
    String httpMethod;
    String requestURI;
    String requestBody;
    HashMap<String, String> headers;

    /**
     * Constructor populates all of the fields necessary for logging.
     * @param mutableHttpServletRequest {@link MutableHttpServletRequest}
     * @param filterContext {@link FilterContext}
     * @throws IOException if there's an issue converting the response body to a string
     */
    public RequestLog(MutableHttpServletRequest mutableHttpServletRequest,
                      FilterContext filterContext) throws IOException {

        preamble = "Intrafilter Request Log";
        timestamp = new DateTime().toString();
        currentFilter = getFilterDescription(filterContext.getFilterConfig());
        httpMethod = mutableHttpServletRequest.getMethod();
        requestURI = mutableHttpServletRequest.getRequestURI();
        headers = convertRequestHeadersToMap(mutableHttpServletRequest);

        //Have to wrap the input stream in something that can be buffered, as well as reset.
        ServletInputStream bin = mutableHttpServletRequest.getInputStream();
        bin.mark(Integer.MAX_VALUE); //Something doesn't support mark reset
        requestBody = IOUtils.toString(bin); //http://stackoverflow.com/a/309448
        bin.reset();
    }

    /**
     * Convert the headers in the request into a HashMap.
     * @param mutableHttpServletRequest {@link MutableHttpServletRequest}
     * @return {@link HashMap}<{@link String}, {@link String}>
     */
    private HashMap<String, String> convertRequestHeadersToMap(
            MutableHttpServletRequest mutableHttpServletRequest) {

        HashMap<String, String> headerMap = new LinkedHashMap<>();
        List<String> headerNames = Collections.list(mutableHttpServletRequest.getHeaderNames());

        for (String headerName : headerNames) {
            headerMap.put(headerName, mutableHttpServletRequest.getHeader(headerName));
        }

        return headerMap;
    }

    /**
     * Creates a filter description using the filter name and (if specified) the filter ID.
     * The filter ID provides context in the event there is more than one filter with the same name.
     * @param filter {@link Filter}
     * @return {@link String} the filter description
     */
    private String getFilterDescription(final Filter filter) {
        if (StringUtils.isEmpty(filter.getId())) {
            return filter.getName();
        } else {
            return filter.getId() + "-" + filter.getName();
        }
    }
}
