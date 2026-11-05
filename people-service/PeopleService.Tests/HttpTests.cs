using System.Net;
using System.Net.Http.Json;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Testing;
using PeopleService.Core.DirectoryService;
using PeopleService.Core.Queries;
using PeopleService.WebApi.Controllers;

namespace PeopleService.Tests;

public sealed class HttpTests : IDisposable
{
    private readonly DirectoryFixture directory = new();
    private readonly WebApplicationFactory<PeopleController> factory;
    private readonly HttpClient client;

    public HttpTests()
    {
        factory = new WebApplicationFactory<PeopleController>().WithWebHostBuilder(builder =>
            builder.UseEnvironment("Testing").UseSetting("PeopleJsonFilePath", directory.FilePath));
        client = factory.CreateClient(new WebApplicationFactoryClientOptions { BaseAddress = new Uri("https://localhost") });
    }

    [Theory]
    [InlineData("LogonId=alice", "alice", "100")]
    [InlineData("EmployeeId=200", "alicia", "200")]
    public async Task Get_person_returns_serialized_directory_person(string query, string logon, string employee)
    {
        var response = await client.GetAsync("/People/GetPerson?" + query);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var person = await response.Content.ReadFromJsonAsync<Person>();
        Assert.Equal(logon, person!.LogonId);
        Assert.Equal(employee, person.EmployeeId);
        Assert.Equal(logon + "@example.test", person.Email);
    }

    [Fact]
    public async Task Matching_returns_limited_people_body()
    {
        var response = await client.GetAsync("/People/GetMatchingPeople?SearchText=ali&Take=1");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var body = await response.Content.ReadFromJsonAsync<GetMatchingPeople.Response>();
        Assert.Equal("alice", Assert.Single(body!.People!).LogonId);
    }

    [Theory]
    [InlineData("GetPerson?LogonId=missing")]
    [InlineData("GetMatchingPeople?SearchText=missing")]
    [InlineData("ValidatePerson?EmployeeId=missing")]
    public async Task Unknown_people_return_404_problem_details(string route)
    {
        var response = await client.GetAsync("/People/" + route);
        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        Assert.Equal(404, (await response.Content.ReadFromJsonAsync<ProblemDetails>())!.Status);
    }

    [Theory]
    [InlineData("GetPerson", "Either LogonId or EmployeeId must be provided")]
    [InlineData("ValidatePerson", "Either LogonId or EmployeeId must be provided")]
    [InlineData("GetMatchingPeople", "SearchText must be provided")]
    [InlineData("GetMatchingPeople?SearchText=ab", "SearchText must be at least 3 characters long")]
    public async Task Invalid_requests_return_400_with_validation_messages(string route, string message)
    {
        var response = await client.GetAsync("/People/" + route);
        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        var problem = await response.Content.ReadFromJsonAsync<ValidationProblemDetails>();
        Assert.Equal(400, problem!.Status);
        Assert.Contains(problem.Errors.SelectMany(e => e.Value), e => e.Contains(message));
    }

    [Theory]
    [InlineData("LogonId=alice")]
    [InlineData("EmployeeId=100")]
    public async Task Valid_person_returns_200_without_attributes(string query)
    {
        var response = await client.GetAsync("/People/ValidatePerson?" + query);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("", await response.Content.ReadAsStringAsync());
    }

    public void Dispose()
    {
        client.Dispose();
        factory.Dispose();
        directory.Dispose();
    }
}
