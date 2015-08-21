package features.filters.clientauthn

import framework.ReposeValveTest
import framework.mocks.MockIdentityService
import org.joda.time.DateTime
import org.rackspace.deproxy.Deproxy
import org.rackspace.deproxy.MessageChain

/**
 * Created by jennyvo on 8/21/15.
 */
class ClientAuthNImpersonateTest extends ReposeValveTest {

    def static originEndpoint
    def static identityEndpoint

    def static MockIdentityService fakeIdentityService

    def setupSpec() {

        deproxy = new Deproxy()

        def params = properties.defaultTemplateParams
        repose.configurationProvider.applyConfigs("common", params)
        repose.configurationProvider.applyConfigs("features/filters/clientauthn/common", params)
        repose.configurationProvider.applyConfigs("features/filters/clientauthn/tenantlessValidation", params)

        repose.start()

        originEndpoint = deproxy.addEndpoint(properties.targetPort, 'origin service')
        fakeIdentityService = new MockIdentityService(properties.identityPort, properties.targetPort)
        identityEndpoint = deproxy.addEndpoint(properties.identityPort,
                'identity service', null, fakeIdentityService.handler)

    }

    def cleanupSpec() {
        deproxy.shutdown()

        repose.stop()
    }

    def "Validates impersonate and x-impersonate-role from headers"() {

        fakeIdentityService.with {
            client_token = UUID.randomUUID().toString()
            tokenExpiresAt = DateTime.now().plusDays(1)
            client_userid = "456"
            impersonate_id = "1234"
            impersonate_name = "repose_test"
        }


        when: "User passes a request through repose"
        MessageChain mc = deproxy.makeRequest(url: reposeEndpoint + "/servers/serrrrrrrr", method: 'GET',
                headers: ['content-type': 'application/json', 'X-Auth-Token': fakeIdentityService.client_token])

        then: "Things are forward to the origin, because we're not validating existence of tenant"
        mc.receivedResponse.code == "200"
        mc.handlings.size() == 1
        !mc.handlings[0].request.headers.contains("X-Impersonator-Name")
        !mc.handlings[0].request.headers.contains("X-Impersonator-Id")
    }
}

