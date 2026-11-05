namespace PeopleService.WebApi.Auth
{
    /// <summary>
    /// Settings for the shared-secret (HS256) TraderX token scheme. All values are
    /// read from the environment; there are no defaults for the secrets.
    /// </summary>
    public class TraderXAuthOptions
    {
        public const string Issuer = "traderx";
        public const string RolesClaim = "roles";
        public const string RoleUser = "USER";
        public const string RoleAdmin = "ADMIN";

        /// <summary>Shared HS256 signing secret (TRADERX_AUTH_SECRET), at least 32 bytes.</summary>
        public string Secret { get; init; } = string.Empty;

        /// <summary>Password accepted for every directory user in this demo (TRADERX_DEMO_USER_PASSWORD).</summary>
        public string DemoUserPassword { get; init; } = string.Empty;

        /// <summary>Logon ids granted the ADMIN role (TRADERX_ADMIN_USERS, comma separated).</summary>
        public IReadOnlySet<string> AdminUsers { get; init; } = new HashSet<string>();

        public TimeSpan TokenLifetime { get; init; } = TimeSpan.FromHours(8);

        public static TraderXAuthOptions FromEnvironment()
        {
            var secret = Environment.GetEnvironmentVariable("TRADERX_AUTH_SECRET") ?? string.Empty;
            if (System.Text.Encoding.UTF8.GetByteCount(secret) < 32)
            {
                throw new InvalidOperationException("TRADERX_AUTH_SECRET must be set and at least 32 bytes long");
            }

            var demoPassword = Environment.GetEnvironmentVariable("TRADERX_DEMO_USER_PASSWORD") ?? string.Empty;
            if (demoPassword.Length < 8)
            {
                throw new InvalidOperationException("TRADERX_DEMO_USER_PASSWORD must be set and at least 8 characters long");
            }

            var admins = (Environment.GetEnvironmentVariable("TRADERX_ADMIN_USERS") ?? string.Empty)
                .Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
                .ToHashSet(StringComparer.OrdinalIgnoreCase);

            return new TraderXAuthOptions
            {
                Secret = secret,
                DemoUserPassword = demoPassword,
                AdminUsers = admins,
            };
        }
    }
}
