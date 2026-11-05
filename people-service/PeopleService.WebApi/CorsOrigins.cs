namespace PeopleService.WebApi;

/// <summary>
/// Cross-origin access is limited to the comma-separated origins in CORS_ALLOWED_ORIGINS.
/// A "*" entry is ignored so the service can never be opened to every origin.
/// </summary>
public static class CorsOrigins
{
    public const string PolicyName = "TraderXCors";
    public const string ConfigurationKey = "CORS_ALLOWED_ORIGINS";
    public const string Default = "http://localhost:8080,http://localhost:18093,http://localhost:18094";

    public static string[] Parse(string? origins) =>
        (origins ?? Default)
            .Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries)
            .Where(origin => origin != "*")
            .ToArray();
}
