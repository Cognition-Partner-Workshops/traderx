using System.Text.Json;
using CacheManager.Core;
using FluentValidation;
using MediatR;
using Microsoft.Extensions.DependencyInjection;
using PeopleService.Core.DirectoryService;
using PeopleService.Core.Infrastructure;
using PeopleService.Core.Queries;

namespace PeopleService.Tests;

public sealed class JsonAndRegistrationTests
{
    [Fact]
    public void Json_reader_preserves_directory_fields()
    {
        using var fixture = new DirectoryFixture();
        var people = JsonFileReader.ReadJsonFile(fixture.FilePath)!;
        Assert.Equal(3, people.Count);
        Assert.Equal(("alice", "Alice Smith", "100", "alice@example.test", "Trading", "/alice.png"),
            (people[0].LogonId, people[0].FullName, people[0].EmployeeId, people[0].Email, people[0].Department, people[0].PhotoUrl));
    }

    [Theory]
    [InlineData("[]", 0)]
    [InlineData("null", null)]
    public void Json_reader_handles_empty_and_null(string json, int? count)
    {
        using var fixture = new DirectoryFixture();
        File.WriteAllText(fixture.FilePath, json);
        Assert.Equal(count, JsonFileReader.ReadJsonFile(fixture.FilePath)?.Count);
    }

    [Fact]
    public void Json_reader_reports_invalid_json_and_missing_file()
    {
        using var fixture = new DirectoryFixture();
        File.WriteAllText(fixture.FilePath, "not json");
        Assert.Throws<JsonException>(() => JsonFileReader.ReadJsonFile(fixture.FilePath));
        File.Delete(fixture.FilePath);
        Assert.Throws<FileNotFoundException>(() => JsonFileReader.ReadJsonFile(fixture.FilePath));
    }

    [Fact]
    public async Task Registration_provides_singleton_directory_validators_handlers_and_cache()
    {
        using var fixture = new DirectoryFixture();
        using var scope = fixture.Services.CreateScope();
        Assert.Same(fixture.Services.GetRequiredService<IDirectoryService>(), scope.ServiceProvider.GetRequiredService<IDirectoryService>());
        Assert.False(fixture.Services.GetRequiredService<IValidator<GetPerson.Request>>().Validate(new GetPerson.Request()).IsValid);
        Assert.False(fixture.Services.GetRequiredService<IValidator<ValidatePerson.Request>>().Validate(new ValidatePerson.Request()).IsValid);
        Assert.False(fixture.Services.GetRequiredService<IValidator<GetMatchingPeople.Request>>().Validate(new GetMatchingPeople.Request()).IsValid);
        await fixture.Mediator.Send(new GetMatchingPeople.Request { SearchText = "ali" });
        Assert.Equal(2, fixture.Services.GetRequiredService<ICacheManager<GetMatchingPeople.Response>>().Get("ali,10").People!.Count);
    }

    [Theory]
    [InlineData("file")]
    [InlineData("directory")]
    [InlineData("json")]
    public void Registration_translates_directory_loading_errors(string kind)
    {
        using var fixture = new DirectoryFixture();
        string path = fixture.FilePath;
        string expected;
        if (kind == "file") { File.Delete(path); expected = $"File not found: {path}"; }
        else if (kind == "directory") { path = Path.Combine(path + "-missing", "people.json"); expected = $"Directory not found: {Path.GetDirectoryName(path)}"; }
        else { File.WriteAllText(path, "invalid"); expected = "Error parsing JSON file: "; }
        var error = Assert.Throws<Exception>(() => new ServiceCollection().AddPeopleServiceCore(DirectoryFixture.Section(path)));
        Assert.StartsWith(expected, error.Message);
    }
}
