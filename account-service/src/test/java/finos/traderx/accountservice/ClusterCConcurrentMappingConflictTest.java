package finos.traderx.accountservice;

import finos.traderx.accountservice.controller.AccountUserController;
import finos.traderx.accountservice.model.AccountUser;
import finos.traderx.accountservice.repository.AccountRepository;
import finos.traderx.accountservice.repository.AccountUserRepository;
import finos.traderx.accountservice.service.AccountUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AccountUserController.class, properties = "people.service.url=http://people.test")
@Import(AccountUserService.class)
class ClusterCConcurrentMappingConflictTest {
    @Autowired MockMvc mvc;
    @Autowired AccountUserController controller;
    @MockBean AccountRepository accounts;
    @MockBean AccountUserRepository users;

    @Test
    void losingConcurrentInsertReturnsGenericConflict() throws Exception {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer people = MockRestServiceServer.bindTo(template).build();
        ReflectionTestUtils.setField(controller, "restTemplate", template);
        people.expect(requestTo("http://people.test/People/GetPerson?LogonId=user01"))
            .andRespond(withSuccess("{\"logonId\":\"user01\"}", MediaType.APPLICATION_JSON));
        when(accounts.existsById(22)).thenReturn(true);
        when(users.existsByAccountIdAndUsername(22, "user01")).thenReturn(false);
        when(users.save(any(AccountUser.class))).thenThrow(new DataIntegrityViolationException("sensitive database details"));

        mvc.perform(post("/accountuser/").contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":22,\"username\":\"user01\"}"))
            .andExpect(status().isConflict())
            .andExpect(content().string("Account user already exists"));
        verify(users).save(any(AccountUser.class));
        people.verify();
    }
}
