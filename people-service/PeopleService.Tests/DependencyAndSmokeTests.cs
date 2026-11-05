using System.Reflection;
using System.Text.Json;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using PeopleService.WebApi.Controllers;
using Xunit;
using Xunit.Abstractions;

namespace PeopleService.Tests;

public class DependencyAndSmokeTests(ITestOutputHelper output)
{
    private static string ServiceRoot
    {
        get
        {
            var directory = new DirectoryInfo(AppContext.BaseDirectory);
            while (directory != null && !File.Exists(Path.Combine(directory.FullName, "PeopleService.sln")))
                directory = directory.Parent;
            return directory?.FullName ?? throw new DirectoryNotFoundException("PeopleService.sln not found");
        }
    }

    [Theory]
    [InlineData("PeopleService.Core")]
    [InlineData("PeopleService.WebApi")]
    public void ResolvedJsonPackageIsPatched(string project)
    {
        using var assets = JsonDocument.Parse(File.ReadAllText(Path.Combine(ServiceRoot, project, "obj", "project.assets.json")));
        var package = assets.RootElement.GetProperty("libraries").EnumerateObject()
            .Single(p => p.Name.StartsWith("System.Text.Json/", StringComparison.Ordinal));
        var version = Version.Parse(package.Name.Split('/')[1]);
        Assert.True(version >= new Version(8, 0, 5), $"{project} resolves {package.Name}");
        var runtimeVersion = typeof(JsonSerializer).Assembly.GetCustomAttribute<AssemblyInformationalVersionAttribute>()!.InformationalVersion;
        output.WriteLine($"Runtime System.Text.Json: {runtimeVersion}");
        Assert.True(Version.Parse(runtimeVersion.Split('+')[0]) >= new Version(8, 0, 5), runtimeVersion);
    }

    [Fact]
    public void LegacyLoggingDependenciesAreAbsent()
    {
        using var assets = JsonDocument.Parse(File.ReadAllText(Path.Combine(ServiceRoot, "PeopleService.WebApi", "obj", "project.assets.json")));
        var names = assets.RootElement.GetProperty("libraries").EnumerateObject().Select(p => p.Name).ToArray();
        Assert.DoesNotContain(names, n => n.StartsWith("Serilog.Extensions.Logging.File/", StringComparison.Ordinal));
        Assert.DoesNotContain(names, n => n.StartsWith("Serilog.Sinks.Async/", StringComparison.Ordinal));
        Assert.DoesNotContain(names, n => n.StartsWith("Serilog.Sinks.RollingFile/", StringComparison.Ordinal));
        Assert.Contains(names, n => n.StartsWith("Serilog.Sinks.File/", StringComparison.Ordinal));
    }

    [Theory]
    [InlineData("/swagger/v1/swagger.json", 200)]
    [InlineData("/People/GetPerson?LogonId=user01", 200)]
    [InlineData("/People/GetMatchingPeople?SearchText=Alice", 200)]
    [InlineData("/People/GetMatchingPeople?SearchText=NobodyMatches", 404)]
    [InlineData("/People/GetMatchingPeople?SearchText=", 400)]
    public async Task ServesHttp(string path, int status)
    {
        await using var factory = new WebApplicationFactory<PeopleController>().WithWebHostBuilder(builder =>
        {
            builder.UseEnvironment("Development");
            builder.UseContentRoot(Path.Combine(ServiceRoot, "PeopleService.WebApi"));
        });
        using var client = factory.CreateClient(new WebApplicationFactoryClientOptions { BaseAddress = new Uri("https://localhost") });
        var response = await client.GetAsync(path);
        Assert.Equal(status, (int)response.StatusCode);
        if (path.Contains("GetMatchingPeople"))
        {
            var cachedResponse = await client.GetAsync(path);
            Assert.Equal(status, (int)cachedResponse.StatusCode);
            if (status == 200)
                Assert.Equal(await response.Content.ReadAsStringAsync(), await cachedResponse.Content.ReadAsStringAsync());
        }
    }
}
