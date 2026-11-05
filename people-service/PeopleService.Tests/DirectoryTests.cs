using Microsoft.Extensions.DependencyInjection;
using PeopleService.Core.DirectoryService;

namespace PeopleService.Tests;

public sealed class DirectoryTests : IDisposable
{
    private readonly DirectoryFixture fixture = new();
    private IDirectoryService Directory => fixture.Services.GetRequiredService<IDirectoryService>();

    [Theory]
    [InlineData("alice", "300", "100")]
    [InlineData("", "200", "200")]
    [InlineData(null, "300", "300")]
    public async Task Lookup_prefers_logon_otherwise_uses_employee(string? logon, string employee, string expected)
    {
        var person = await Directory.GetPerson(logon!, employee);
        Assert.Equal(expected, person?.EmployeeId);
    }

    [Theory]
    [InlineData("missing", "100")]
    [InlineData("ALICE", "100")]
    [InlineData("", "missing")]
    [InlineData("", "")]
    public async Task Unknown_identity_is_not_found_or_valid(string logon, string employee)
    {
        Assert.Null(await Directory.GetPerson(logon, employee));
        Assert.False(await Directory.ValidatePerson(logon, employee));
    }

    [Theory]
    [InlineData("alice", "")]
    [InlineData("", "100")]
    public async Task Existing_identity_is_valid(string logon, string employee) =>
        Assert.True(await Directory.ValidatePerson(logon, employee));

    [Theory]
    [InlineData("ali", 10, "alice,alicia")]
    [InlineData("Smith", 10, "alice,bob")]
    [InlineData("ali", 1, "alice")]
    [InlineData("ali", 0, "")]
    [InlineData("ali", -1, "")]
    [InlineData("missing", 10, "")]
    [InlineData("ALICE", 10, "")]
    public async Task Matching_filters_name_or_logon_and_limits_in_directory_order(string text, int take, string expected)
    {
        var people = await Directory.GetMatchingPerson(text, take);
        Assert.Equal(expected, string.Join(",", people!.Select(p => p.LogonId)));
    }

    [Fact]
    public async Task Null_search_throws_instead_of_matching_everyone() =>
        await Assert.ThrowsAsync<ArgumentNullException>(async () => (await Directory.GetMatchingPerson(null!, 10))!.ToList());

    public void Dispose() => fixture.Dispose();
}
