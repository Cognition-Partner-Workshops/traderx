package finos.traderx.accountservice;

import finos.traderx.accountservice.controller.*;
import finos.traderx.accountservice.exceptions.ResourceNotFoundException;
import finos.traderx.accountservice.model.*;
import finos.traderx.accountservice.service.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ControllerTests {
    AccountService accounts;
    AccountUserService users;
    MockMvc mvc;
    MockRestServiceServer people;

    @BeforeEach void setup() {
        accounts = mock(AccountService.class);
        users = mock(AccountUserService.class);
        AccountController accountController = new AccountController();
        AccountUserController userController = new AccountUserController();
        ReflectionTestUtils.setField(accountController, "accountService", accounts);
        ReflectionTestUtils.setField(userController, "accountUserService", users);
        RestTemplate rest = new RestTemplate();
        people = MockRestServiceServer.bindTo(rest).build();
        ReflectionTestUtils.setField(userController, "restTemplate", rest);
        ReflectionTestUtils.setField(userController, "peopleServiceAddress", "http://people.test");
        mvc = MockMvcBuilders.standaloneSetup(accountController, userController, new DocsController()).build();
    }

    @Test void getsAccountAsJson() throws Exception {
        Account account = new Account();
        account.setId(42);
        account.setDisplayName("Trading");
        when(accounts.getAccountById(42)).thenReturn(account);
        mvc.perform(get("/account/42")).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(42)).andExpect(jsonPath("$.displayName").value("Trading"));
    }

    @ParameterizedTest @ValueSource(strings = {"/account/", "/accountuser/"})
    void emptyListsAreJsonArrays(String path) throws Exception {
        when(accounts.getAllAccount()).thenReturn(List.of());
        when(users.getAllAccountUsers()).thenReturn(List.of());
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void listsAccountsAndUsers() throws Exception {
        Account account = new Account();
        account.setDisplayName("Trading");
        when(accounts.getAllAccount()).thenReturn(List.of(account));
        when(users.getAllAccountUsers()).thenReturn(List.of(user()));
        mvc.perform(get("/account/")).andExpect(status().isOk()).andExpect(jsonPath("$[0].displayName").value("Trading"));
        mvc.perform(get("/accountuser/")).andExpect(status().isOk()).andExpect(jsonPath("$[0].username").value("alice"));
    }

    @ParameterizedTest @ValueSource(strings = {"POST", "PUT"})
    void writesAccountAndReturnsSavedBody(String method) throws Exception {
        Account saved = new Account();
        saved.setId(42);
        saved.setDisplayName("Trading");
        when(accounts.upsertAccount(any())).thenAnswer(call -> {
            Account input = call.getArgument(0);
            org.assertj.core.api.Assertions.assertThat(input.getDisplayName()).isEqualTo("Trading");
            return saved;
        });
        mvc.perform(request(HttpMethod.valueOf(method), "/account/").contentType(MediaType.APPLICATION_JSON)
            .content("{\"displayName\":\"Trading\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.displayName").value("Trading"));
        verify(accounts).upsertAccount(any());
    }

    @Test void getsUser() throws Exception {
        when(users.getAccountUserById(42)).thenReturn(user());
        mvc.perform(get("/accountuser/42")).andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(42)).andExpect(jsonPath("$.username").value("alice"));
    }

    @ParameterizedTest @ValueSource(strings = {"/account/42", "/accountuser/42"})
    void missingResourcesReturn404AndMessage(String path) throws Exception {
        when(accounts.getAccountById(42)).thenThrow(new ResourceNotFoundException("missing account"));
        when(users.getAccountUserById(42)).thenThrow(new ResourceNotFoundException("missing account"));
        mvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(content().string("missing account"));
    }

    @ParameterizedTest @ValueSource(strings = {"/account/42", "/accountuser/42"})
    void unexpectedErrorsReturn500AndMessage(String path) throws Exception {
        when(accounts.getAccountById(42)).thenThrow(new IllegalStateException("database unavailable"));
        when(users.getAccountUserById(42)).thenThrow(new IllegalStateException("database unavailable"));
        mvc.perform(get(path)).andExpect(status().isInternalServerError()).andExpect(content().string("database unavailable"));
    }

    @Test void createsUserAfterPeopleLookup() throws Exception {
        people.expect(requestTo("http://people.test/People/GetPerson?LogonId=alice"))
            .andExpect(method(HttpMethod.GET)).andRespond(withSuccess("{\"logonId\":\"alice\",\"fullName\":\"Alice\"}", MediaType.APPLICATION_JSON));
        when(users.upsertAccountUser(any())).thenAnswer(call -> call.getArgument(0));
        mvc.perform(post("/accountuser/").contentType(MediaType.APPLICATION_JSON).content(userJson()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.accountId").value(42));
        verify(users).upsertAccountUser(any());
        people.verify();
    }

    @ParameterizedTest @ValueSource(ints = {404, 400})
    void rejectsPeopleClientErrorsWithoutSaving(int status) throws Exception {
        people.expect(requestTo("http://people.test/People/GetPerson?LogonId=alice"))
            .andRespond(withStatus(org.springframework.http.HttpStatus.valueOf(status)));
        mvc.perform(post("/accountuser/").contentType(MediaType.APPLICATION_JSON).content(userJson()))
            .andExpect(status().isNotFound()).andExpect(content().string("alice not found in People service."));
        verifyNoInteractions(users);
        people.verify();
    }

    @Test void peopleServerFailureDoesNotSaveUser() throws Exception {
        people.expect(requestTo("http://people.test/People/GetPerson?LogonId=alice")).andRespond(withServerError());
        mvc.perform(post("/accountuser/").contentType(MediaType.APPLICATION_JSON).content(userJson()))
            .andExpect(status().isInternalServerError());
        verifyNoInteractions(users);
        people.verify();
    }

    @Test void updatesUserWithoutPeopleLookup() throws Exception {
        when(users.upsertAccountUser(any())).thenAnswer(call -> call.getArgument(0));
        mvc.perform(put("/accountuser/").contentType(MediaType.APPLICATION_JSON).content(userJson()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(42))
            .andExpect(jsonPath("$.username").value("alice"));
        verify(users).upsertAccountUser(any());
        people.verify();
    }

    @Test void docsRedirectToSwagger() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("swagger-ui.html"));
    }

    private static AccountUser user() {
        AccountUser user = new AccountUser();
        user.setAccountId(42);
        user.setUsername("alice");
        return user;
    }

    private static String userJson() {
        return "{\"accountId\":42,\"username\":\"alice\"}";
    }
}
