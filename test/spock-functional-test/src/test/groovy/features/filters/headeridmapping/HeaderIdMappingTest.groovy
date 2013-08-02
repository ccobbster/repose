package features.filters.headeridmapping

import framework.ReposeValveTest
import org.rackspace.gdeproxy.Deproxy
import org.rackspace.gdeproxy.Handling
import org.rackspace.gdeproxy.MessageChain
import org.rackspace.gdeproxy.Response
import spock.lang.Unroll

class HeaderIdMappingTest extends ReposeValveTest {

    def static String user1 = "reposeuser1"
    def static String user2 = "reposeuser2"
    def static Map headersIdSingle = ["x-rax-user": user1, "x-rax-groups": "reposegroup1"]
    def static Map headersIdMulti = ["x-rax-user": user2+","+user1, "x-rax-groups": "reposegroup1"]

    def setupSpec() {
        deproxy = new Deproxy()
        deproxy.addEndpoint(properties.getProperty("target.port").toInteger())

        repose.applyConfigs("features/filters/headeridmapping")
        repose.start()
        waitUntilReadyToServiceRequests()
    }

    def cleanupSpec() {
        if (deproxy) {
            deproxy.shutdown()
        }

        if (repose) {
            repose.stop()
        }
    }

    @Unroll("Sent headers: #incomingHeaders")
    def "when identifying requests by header"() {

        when: "Request contains a single value of the target header"
        def messageChain = deproxy.makeRequest([url: reposeEndpoint, headers: incomingHeaders])
        def sentRequest = ((MessageChain) messageChain).getHandlings()[0]

        then: "Repose will send x-pp-user with a single value"
        ((Handling) sentRequest).request.getHeaders().findAll("x-pp-user").contains(expecteduser+";q=0.2")
        ((Handling) sentRequest).request.getHeaders().findAll("x-pp-user").size() == 1
        ((Handling) sentRequest).request.getHeaders().findAll("x-pp-groups").contains("reposegroup1;q=0.2")

        where:
        incomingHeaders | expecteduser
        headersIdSingle | user1
        headersIdMulti  | user2


    }
}
