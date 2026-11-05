using System.Net;
using System.Text.Json;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using PeopleService.WebApi.Controllers;
using Xunit;

namespace PeopleService.Core.Tests;

public class MatchingPeopleHttpValidationTests
{
    [Theory]
    [InlineData(1000, HttpStatusCode.BadRequest)]
    [InlineData(5, HttpStatusCode.OK)]
    public async Task AutoValidationEnforcesTakeRange(int take, HttpStatusCode expected)
    {
        await using var factory = new WebApplicationFactory<PeopleController>()
            .WithWebHostBuilder(builder => builder.UseSetting(
                "PeopleJsonFilePath", Path.Combine(AppContext.BaseDirectory, "people.json")));
        using var client = factory.CreateClient(new WebApplicationFactoryClientOptions
        {
            BaseAddress = new Uri("https://localhost")
        });
        using var response = await client.GetAsync($"/People/GetMatchingPeople?SearchText=user&Take={take}");
        Assert.Equal(expected, response.StatusCode);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        if (expected == HttpStatusCode.BadRequest)
        {
            Assert.Equal("Take must be between 1 and 100",
                body.RootElement.GetProperty("errors").GetProperty("Take")[0].GetString());
        }
        else
        {
            Assert.Equal(take, body.RootElement.GetProperty("people").GetArrayLength());
        }
    }
}
