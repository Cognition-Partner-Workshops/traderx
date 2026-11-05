using System.Net;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Xunit;

namespace PeopleService.WebApi.Tests;

public class EdgeHardeningTests
{
    private static HttpClient CreateClient(params (string Key, string Value)[] settings)
    {
        var factory = new WebApplicationFactory<Program>().WithWebHostBuilder(builder =>
        {
            // Development is what the container runs with (launchSettings), i.e. the riskiest case.
            builder.UseEnvironment("Development");
            builder.UseSetting("https_port", "443");
            foreach (var (key, value) in settings)
            {
                builder.UseSetting(key, value);
            }
        });
        return factory.CreateClient(new WebApplicationFactoryClientOptions { AllowAutoRedirect = false });
    }

    [Fact]
    public async Task Http_request_is_not_redirected_to_https_by_default()
    {
        var response = await CreateClient().GetAsync("/People/GetMatchingPeople?SearchText=a&Take=1");

        Assert.NotEqual(HttpStatusCode.TemporaryRedirect, response.StatusCode);
        Assert.Null(response.Headers.Location);
    }

    [Fact]
    public async Task Http_request_is_redirected_when_https_redirection_enabled()
    {
        var response = await CreateClient(("HttpsRedirection:Enabled", "true"))
            .GetAsync("/People/GetMatchingPeople?SearchText=a&Take=1");

        Assert.Equal(HttpStatusCode.TemporaryRedirect, response.StatusCode);
        Assert.Equal("https", response.Headers.Location?.Scheme);
    }

    [Theory]
    [InlineData("/swagger/v1/swagger.json")]
    [InlineData("/swagger/index.html")]
    public async Task Swagger_is_not_exposed_by_default_even_in_development(string path)
    {
        var response = await CreateClient().GetAsync(path);

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Fact]
    public async Task Swagger_is_served_when_enabled()
    {
        var response = await CreateClient(("Swagger:Enabled", "true")).GetAsync("/swagger/v1/swagger.json");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }
}
