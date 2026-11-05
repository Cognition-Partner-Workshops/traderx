using MediatR;
using CacheManager.Core;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using PeopleService.Core.DirectoryService;
using PeopleService.Core.Queries;
using PeopleService.WebApi.Controllers;
using Xunit;

namespace PeopleService.SecurityTests;

public class LogInjectionTests
{
    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task D3_AllDirectoryPathsUseStructuredSingleLineLogValues(bool found)
    {
        var directory = new DirectoryStub(found);
        using var services = new ServiceCollection()
            .AddSingleton<IDirectoryService>(directory)
            .AddMediatR(config => config.RegisterServicesFromAssemblyContaining<GetPerson.Request>())
            .AddCacheManager<GetMatchingPeople.Response>(config => config.WithDictionaryHandle())
            .BuildServiceProvider();
        var logger = new CaptureLogger();
        var controller = new PeopleController(services.GetRequiredService<IMediator>(), logger);
        const string input = "user\r\nFORGED";
        await controller.GetPerson(new GetPerson.Request { LogonId = input, EmployeeId = input });
        await controller.GetMatchingPeople(new GetMatchingPeople.Request { SearchText = input });
        await controller.ValidatePerson(new ValidatePerson.Request { LogonId = input, EmployeeId = input });

        Assert.Equal(6, logger.Messages.Count);
        foreach (var (message, fields) in logger.Messages)
        {
            Assert.Contains("user__FORGED", message);
            Assert.DoesNotContain("\r", message);
            Assert.DoesNotContain("\n", message);
            Assert.Contains(fields, field => field.Key != "{OriginalFormat}" && Equals(field.Value, "user__FORGED"));
        }
        // Logging must never change the values used by the directory query.
        Assert.All(directory.Inputs, value => Assert.Equal(input, value));
    }

    private sealed class DirectoryStub(bool found) : IDirectoryService
    {
        public List<string> Inputs { get; } = new();

        public Task<Person?> GetPerson(string logonId, string employeeId)
        {
            Inputs.Add(logonId);
            Inputs.Add(employeeId);
            return Task.FromResult(found ? new Person() : null);
        }

        public Task<IEnumerable<Person>?> GetMatchingPerson(string searchText, int take)
        {
            Inputs.Add(searchText);
            return Task.FromResult<IEnumerable<Person>?>(found ? new[] { new Person() } : Array.Empty<Person>());
        }

        public Task<bool> ValidatePerson(string logonId, string employeeId)
        {
            Inputs.Add(logonId);
            Inputs.Add(employeeId);
            return Task.FromResult(found);
        }
    }

    private sealed class CaptureLogger : ILogger<PeopleController>
    {
        public List<(string Message, List<KeyValuePair<string, object?>> Fields)> Messages { get; } = new();
        public IDisposable? BeginScope<TState>(TState state) where TState : notnull => null;
        public bool IsEnabled(LogLevel level) => true;

        public void Log<TState>(LogLevel level, EventId id, TState state, Exception? error,
            Func<TState, Exception?, string> formatter)
        {
            var fields = Assert.IsAssignableFrom<IEnumerable<KeyValuePair<string, object?>>>(state);
            Messages.Add((formatter(state, error), fields.ToList()));
        }
    }
}
