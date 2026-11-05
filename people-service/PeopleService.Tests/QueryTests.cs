using CacheManager.Core;
using MediatR;
using Microsoft.Extensions.DependencyInjection;
using PeopleService.Core.DirectoryService;
using PeopleService.Core.Queries;

namespace PeopleService.Tests;

public sealed class QueryTests : IDisposable
{
    private readonly DirectoryFixture fixture = new();

    [Theory]
    [InlineData("alice", "", "100", true)]
    [InlineData(null, "200", "200", true)]
    [InlineData("missing", "", null, false)]
    public async Task Identity_handlers_return_person_and_validation_result(string? logon, string employee, string? expected, bool valid)
    {
        Assert.Equal(expected, (await fixture.Mediator.Send(new GetPerson.Request { LogonId = logon, EmployeeId = employee }))?.EmployeeId);
        Assert.Equal(valid, (await fixture.Mediator.Send(new ValidatePerson.Request { LogonId = logon, EmployeeId = employee })).IsValid);
    }

    [Theory]
    [InlineData("ali", 2, "alice,alicia")]
    [InlineData("ali", 1, "alice")]
    [InlineData("missing", 10, null)]
    [InlineData("ali", 0, null)]
    public async Task Matching_handler_returns_and_caches_results_including_misses(string text, int take, string? expected)
    {
        var request = new GetMatchingPeople.Request { SearchText = text, Take = take };
        var first = await fixture.Mediator.Send(request);
        var cached = await fixture.Mediator.Send(request);
        if (expected == null) { Assert.Null(first); Assert.Null(cached); }
        else
        {
            Assert.Equal(expected, string.Join(",", first!.People!.Select(p => p.LogonId)));
            Assert.Same(first, cached);
        }
        Assert.Equal(expected == null ? 0 : take, fixture.Services.GetRequiredService<ICacheManager<GetMatchingPeople.Response>>().Get($"{text},{take}").People!.Count);
    }

    [Theory]
    [InlineData(null, null, false)]
    [InlineData(" ", "\t", false)]
    [InlineData("alice", null, true)]
    [InlineData(null, "100", true)]
    public void Identity_validators_require_at_least_one_nonblank_identifier(string? logon, string? employee, bool valid)
    {
        var person = new GetPerson.RequestValidator().Validate(new GetPerson.Request { LogonId = logon, EmployeeId = employee });
        var validation = new ValidatePerson.RequestValidator().Validate(new ValidatePerson.Request { LogonId = logon, EmployeeId = employee });
        Assert.Equal(valid, person.IsValid);
        Assert.Equal(valid, validation.IsValid);
        if (!valid)
        {
            Assert.Contains("Either LogonId or EmployeeId must be provided", Assert.Single(person.Errors).ErrorMessage);
            Assert.Contains("Either LogonId or EmployeeId must be provided", Assert.Single(validation.Errors).ErrorMessage);
        }
    }

    [Theory]
    [InlineData(null, "SearchText must be provided")]
    [InlineData(" ", "SearchText must be provided")]
    [InlineData("ab", "SearchText must be at least 3 characters long")]
    [InlineData("abc", null)]
    public void Matching_validator_enforces_search_boundary(string? text, string? message)
    {
        var result = new GetMatchingPeople.RequestValidator().Validate(new GetMatchingPeople.Request { SearchText = text });
        Assert.Equal(message == null, result.IsValid);
        if (message != null) Assert.Equal(message, Assert.Single(result.Errors).ErrorMessage);
    }

    [Theory]
    [InlineData("person")]
    [InlineData("matching")]
    [InlineData("validate")]
    public async Task Handlers_propagate_directory_failures(string query)
    {
        using var services = new ServiceCollection().AddLogging()
            .AddMediatR(c => c.RegisterServicesFromAssembly(typeof(GetPerson).Assembly))
            .AddSingleton<IDirectoryService, FailingDirectory>()
            .AddSingleton(fixture.Services.GetRequiredService<ICacheManager<GetMatchingPeople.Response>>())
            .BuildServiceProvider();
        var mediator = services.GetRequiredService<IMediator>();
        var error = await Assert.ThrowsAsync<IOException>(async () =>
        {
            if (query == "person") await mediator.Send(new GetPerson.Request { LogonId = "alice" });
            else if (query == "matching") await mediator.Send(new GetMatchingPeople.Request { SearchText = "ali" });
            else await mediator.Send(new ValidatePerson.Request { EmployeeId = "100" });
        });
        Assert.Equal("Directory unavailable", error.Message);
    }

    private sealed class FailingDirectory : IDirectoryService
    {
        public Task<Person?> GetPerson(string logonId, string employeeId) => throw new IOException("Directory unavailable");
        public Task<IEnumerable<Person>?> GetMatchingPerson(string searchText, int take) => throw new IOException("Directory unavailable");
        public Task<bool> ValidatePerson(string logonId, string employeeId) => throw new IOException("Directory unavailable");
    }

    public void Dispose() => fixture.Dispose();
}
