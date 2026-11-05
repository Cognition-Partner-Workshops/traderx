using System.Net;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using PeopleService.WebApi;
using Xunit;

namespace PeopleService.WebApi.Tests;

public class CorsTests
{
    private const string DisallowedOrigin = "https://evil.example.com";
    private const string Endpoint = "/People/GetMatchingPeople?SearchText=user";

    private static HttpClient CreateClient(string? allowedOrigins = null)
    {
        var factory = new WebApplicationFactory<Program>().WithWebHostBuilder(builder =>
        {
            if (allowedOrigins != null)
            {
                builder.UseSetting(CorsOrigins.ConfigurationKey, allowedOrigins);
            }
        });
        return factory.CreateClient();
    }

    private static HttpRequestMessage Preflight(string origin)
    {
        var request = new HttpRequestMessage(HttpMethod.Options, Endpoint);
        request.Headers.Add("Origin", origin);
        request.Headers.Add("Access-Control-Request-Method", "GET");
        return request;
    }

    private static HttpRequestMessage Get(string origin)
    {
        var request = new HttpRequestMessage(HttpMethod.Get, Endpoint);
        request.Headers.Add("Origin", origin);
        return request;
    }

    [Theory]
    [InlineData("http://localhost:8080")]
    [InlineData("http://localhost:18093")]
    [InlineData("http://localhost:18094")]
    public async Task DefaultLocalUiOrigin_GetsAllowOriginHeader(string origin)
    {
        using var client = CreateClient();

        var preflight = await client.SendAsync(Preflight(origin));
        var get = await client.SendAsync(Get(origin));

        Assert.Equal(HttpStatusCode.NoContent, preflight.StatusCode);
        Assert.Equal(origin, Assert.Single(preflight.Headers.GetValues("Access-Control-Allow-Origin")));
        Assert.Equal(origin, Assert.Single(get.Headers.GetValues("Access-Control-Allow-Origin")));
    }

    [Fact]
    public async Task DisallowedOrigin_GetsNoAllowOriginHeader()
    {
        using var client = CreateClient();

        var preflight = await client.SendAsync(Preflight(DisallowedOrigin));
        var get = await client.SendAsync(Get(DisallowedOrigin));

        Assert.False(preflight.Headers.Contains("Access-Control-Allow-Origin"));
        Assert.False(get.Headers.Contains("Access-Control-Allow-Origin"));
    }

    [Fact]
    public async Task ConfiguredOrigins_ReplaceDefaults_AndWildcardIsIgnored()
    {
        using var client = CreateClient("https://traderx.example.com, *");

        var configured = await client.SendAsync(Get("https://traderx.example.com"));
        var localDefault = await client.SendAsync(Get("http://localhost:18093"));
        var disallowed = await client.SendAsync(Get(DisallowedOrigin));

        Assert.Equal("https://traderx.example.com", Assert.Single(configured.Headers.GetValues("Access-Control-Allow-Origin")));
        Assert.False(localDefault.Headers.Contains("Access-Control-Allow-Origin"));
        Assert.False(disallowed.Headers.Contains("Access-Control-Allow-Origin"));
    }

    [Fact]
    public async Task DisallowedMethod_IsNotAllowedInPreflight()
    {
        using var client = CreateClient();
        var request = new HttpRequestMessage(HttpMethod.Options, Endpoint);
        request.Headers.Add("Origin", "http://localhost:18093");
        request.Headers.Add("Access-Control-Request-Method", "DELETE");

        var response = await client.SendAsync(request);

        Assert.False(response.Headers.Contains("Access-Control-Allow-Methods")
            && response.Headers.GetValues("Access-Control-Allow-Methods").Any(m => m.Contains("DELETE")));
    }

    [Fact]
    public void Parse_TrimsEntries_AndDropsBlankAndWildcard()
    {
        Assert.Equal(new[] { "https://a.example.com" }, CorsOrigins.Parse(" * , ,https://a.example.com"));
        Assert.Equal(new[] { "http://localhost:8080", "http://localhost:18093", "http://localhost:18094" }, CorsOrigins.Parse(null));
        Assert.Empty(CorsOrigins.Parse(""));
    }
}
