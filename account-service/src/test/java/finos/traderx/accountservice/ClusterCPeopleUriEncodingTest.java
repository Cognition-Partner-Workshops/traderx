package finos.traderx.accountservice;

import finos.traderx.accountservice.controller.AccountUserController;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClusterCPeopleUriEncodingTest {
    @Test
    void queryInjectionRemainsOneEncodedValueEvenWithoutBeanValidation() {
        AccountUserController controller = new AccountUserController();
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        ReflectionTestUtils.setField(controller, "restTemplate", template);
        ReflectionTestUtils.setField(controller, "peopleServiceAddress", "http://people.test");
        server.expect(requestTo("http://people.test/People/GetPerson?LogonId=x%26EmployeeId%3D1"))
            .andRespond(withSuccess("{\"logonId\":\"user01\"}", MediaType.APPLICATION_JSON));
        assertEquals(Boolean.TRUE, ReflectionTestUtils.invokeMethod(controller, "validatePerson", "x&EmployeeId=1"));
        server.verify();
    }
}
