package ee.sk.siddemo.client;

/*-
 * #%L
 * Smart-ID sample Java client
 * %%
 * Copyright (C) 2018 - 2025 SK ID Solutions AS
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import ee.sk.siddemo.model.DeviceLinkMockRequest;

@Component
public class SmartIdMockDeviceLinkClient {

    private static final Logger logger = LoggerFactory.getLogger(SmartIdMockDeviceLinkClient.class);

    private final RestClient smartIdMockRestClient;

    public SmartIdMockDeviceLinkClient(RestClient smartIdMockRestClient) {
        this.smartIdMockRestClient = smartIdMockRestClient;
    }

    public void mock(DeviceLinkMockRequest request) {
        logger.info("Mocking for {}", request);
        smartIdMockRestClient.post()
                .uri("/device-link")
                .header("Content-Type", "application/json")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
