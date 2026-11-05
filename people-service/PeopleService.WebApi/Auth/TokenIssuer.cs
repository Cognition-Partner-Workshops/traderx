using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using Microsoft.IdentityModel.Tokens;
using PeopleService.Core.DirectoryService;

namespace PeopleService.WebApi.Auth
{
    /// <summary>
    /// Issues HS256 TraderX bearer tokens for directory users who present the demo password.
    /// </summary>
    public class TokenIssuer
    {
        private readonly TraderXAuthOptions _options;
        private readonly SymmetricSecurityKey _key;

        public TokenIssuer(TraderXAuthOptions options)
        {
            _options = options;
            _key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(options.Secret));
        }

        public bool PasswordMatches(string? password)
        {
            if (string.IsNullOrEmpty(password))
            {
                return false;
            }

            var expected = Encoding.UTF8.GetBytes(_options.DemoUserPassword);
            var actual = Encoding.UTF8.GetBytes(password);
            return CryptographicOperations.FixedTimeEquals(expected, actual);
        }

        public IReadOnlyList<string> RolesFor(Person person)
        {
            var roles = new List<string> { TraderXAuthOptions.RoleUser };
            if (person.LogonId != null && _options.AdminUsers.Contains(person.LogonId))
            {
                roles.Add(TraderXAuthOptions.RoleAdmin);
            }
            return roles;
        }

        public (string Token, DateTime ExpiresAt, IReadOnlyList<string> Roles) Issue(Person person)
        {
            var roles = RolesFor(person);
            var now = DateTime.UtcNow;
            var expires = now.Add(_options.TokenLifetime);

            var claims = new List<Claim>
            {
                new(JwtRegisteredClaimNames.Sub, person.LogonId ?? string.Empty),
                new(JwtRegisteredClaimNames.Iat, new DateTimeOffset(now).ToUnixTimeSeconds().ToString(), ClaimValueTypes.Integer64),
            };
            if (!string.IsNullOrEmpty(person.FullName))
            {
                claims.Add(new Claim(JwtRegisteredClaimNames.Name, person.FullName));
            }
            claims.AddRange(roles.Select(r => new Claim(TraderXAuthOptions.RolesClaim, r)));

            var token = new JwtSecurityToken(
                issuer: TraderXAuthOptions.Issuer,
                claims: claims,
                notBefore: now,
                expires: expires,
                signingCredentials: new SigningCredentials(_key, SecurityAlgorithms.HmacSha256));

            return (new JwtSecurityTokenHandler().WriteToken(token), expires, roles);
        }
    }
}
