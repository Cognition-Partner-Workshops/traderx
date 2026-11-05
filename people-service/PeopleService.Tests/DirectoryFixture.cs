using MediatR;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using PeopleService.Core.Infrastructure;

namespace PeopleService.Tests;

public sealed class DirectoryFixture : IDisposable
{
    public string FilePath { get; } = Path.Combine(Path.GetTempPath(), Guid.NewGuid() + ".json");
    public ServiceProvider Services { get; }
    public IMediator Mediator => Services.GetRequiredService<IMediator>();

    public DirectoryFixture()
    {
        File.WriteAllText(FilePath, """
            [
              {"LogonId":"alice","FullName":"Alice Smith","EmployeeId":"100","Email":"alice@example.test","Department":"Trading","PhotoUrl":"/alice.png"},
              {"LogonId":"alicia","FullName":"Alicia Jones","EmployeeId":"200","Email":"alicia@example.test","Department":"Risk","PhotoUrl":"/alicia.png"},
              {"LogonId":"bob","FullName":"Bob Smith","EmployeeId":"300","Email":"bob@example.test","Department":"Trading","PhotoUrl":"/bob.png"}
            ]
            """);
        Services = new ServiceCollection().AddLogging()
            .AddPeopleServiceCore(Section(FilePath)).BuildServiceProvider();
    }

    public static IConfigurationSection Section(string path) => new ConfigurationBuilder()
        .AddInMemoryCollection(new Dictionary<string, string?> { ["PeopleJsonFilePath"] = path })
        .Build().GetSection("PeopleJsonFilePath");

    public void Dispose()
    {
        Services.Dispose();
        File.Delete(FilePath);
    }
}
