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
package features.filters.clientauthn

import framework.ReposeValveTest
import framework.mocks.MockIdentityService
import org.joda.time.DateTime
import org.rackspace.deproxy.Deproxy
import org.rackspace.deproxy.MessageChain
import spock.lang.Ignore

/**
 * Created by jennyvo on 8/28/15.
 */
class ClientAuthFilterAddHeadersTest extends ReposeValveTest {

    def static originEndpoint
    def static identityEndpoint

    def static MockIdentityService fakeIdentityService

    def setupSpec() {

        deproxy = new Deproxy()
        def params = properties.defaultTemplateParams
        repose.configurationProvider.applyConfigs("common", params)
        repose.configurationProvider.applyConfigs("features/filters/clientauthn/common", params)
        repose.configurationProvider.applyConfigs("features/filters/clientauthn/multitenantheader", params)
        repose.start()

        originEndpoint = deproxy.addEndpoint(properties.targetPort, 'origin service')
        fakeIdentityService = new MockIdentityService(properties.identityPort, properties.targetPort)
        identityEndpoint = deproxy.addEndpoint(properties.identityPort,
                'identity service', null, fakeIdentityService.handler)


    }

    def cleanupSpec() {
        if (deproxy)
            deproxy.shutdown()
        if (repose)
            repose.stop()
    }

    def setup() {
        fakeIdentityService.resetHandlers()
        fakeIdentityService.resetDefaultParameters()
    }

    // REP-2464: Auth filter should add headers not replace headers
    // Note: V2 x-pp-user using username
    def "Verify V2 Auth filter should add headers instead of replace headers"() {
        given:
        fakeIdentityService.with {
            client_token = UUID.randomUUID().toString()
            tokenExpiresAt = (new DateTime()).plusDays(1);
            client_tenant = "12345"
            client_tenant_file = "nast-id"
            service_admin_role = "not-admin"
        }

        when:
        "User passes a request through repose with tenant"
        MessageChain mc = deproxy.makeRequest(
                url: "$reposeEndpoint/servers/12345",
                method: 'GET',
                headers: [
                        'content-type': 'application/json',
                        'X-Auth-Token': fakeIdentityService.client_token,
                        'x-tenant-id' : 'repose-add-tenant',
                        'x-pp-groups' : 'repose-test-add-group',
                        'x-pp-user'   : 'repose-test-add-user',
                        'x-roles'  : 'test-add-role'])

        then: "Everything gets passed as is to the origin service (no matter the user)"
        mc.receivedResponse.code == "200"
        mc.handlings.size() == 1
        (mc.handlings[0].request.headers.findAll("x-tenant-id").toString()).contains("12345")
        (mc.handlings[0].request.headers.findAll("x-tenant-id").toString()).contains("nast-id")
        (mc.handlings[0].request.headers.findAll("x-tenant-id").toString()).contains("repose-add-tenant")
        (mc.handlings[0].request.headers.findAll("x-pp-groups").toString()).contains("0")
        (mc.handlings[0].request.headers.findAll("x-pp-groups").toString()).contains("repose-test-add-group")
        (mc.handlings[0].request.headers.findAll("x-roles").toString()).contains("not-admin")
        (mc.handlings[0].request.headers.findAll("x-roles").toString()).contains("compute:default")
        (mc.handlings[0].request.headers.findAll("x-roles").toString()).contains("object-store:default")
        (mc.handlings[0].request.headers.findAll("x-roles").toString()).contains("test-add-role")
        (mc.handlings[0].request.headers.findAll("x-pp-user").toString()).contains("repose-test-add-user")
        (mc.handlings[0].request.headers.findAll("x-pp-user").toString()).contains("username")
    }

    // REP-2464: Auth filter should add headers not replace headers
    @Ignore ("We can turn on when impersonator role to header merge in to branch")
    def "Verify with impersonation, repose should add x-impersonator-roles headers"() {
        given:
        fakeIdentityService.with {
            client_token = UUID.randomUUID().toString()
            tokenExpiresAt = DateTime.now().plusDays(1)
            client_userid = 123456
            client_tenant = "12345"
            impersonate_name = "impersonator_name"
            impersonate_id = "567"
        }

        when: "User passes a request with impersonation through repose"
        MessageChain mc = deproxy.makeRequest(
                url: "$reposeEndpoint/servers/12345/",
                method: 'GET',
                headers: [
                        'content-type'        : 'application/json',
                        'X-Subject-Token'     : fakeIdentityService.client_token,
                        'x-impersonator-roles': 'repose-test'
                ]
        )

        then: "repose should add X-Impersonator-Name and X-Impersonator-Id"
        mc.receivedResponse.code == "200"
        mc.handlings.size() == 1
        mc.handlings[0].request.headers.getFirstValue("X-Impersonator-Name") == fakeIdentityService.impersonate_name
        mc.handlings[0].request.headers.getFirstValue("X-Impersonator-Id") == fakeIdentityService.impersonate_id
        (mc.handlings[0].request.headers.findAll("x-impersonator-roles").toString()).contains("Racker")
        (mc.handlings[0].request.headers.findAll("x-impersonator-roles").toString()).contains("object-store:admin")
        (mc.handlings[0].request.headers.findAll("x-impersonator-roles").toString()).contains("repose-test")
    }

}
