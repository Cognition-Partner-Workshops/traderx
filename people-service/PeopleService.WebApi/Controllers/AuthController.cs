using System.ComponentModel.DataAnnotations;
using MediatR;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using PeopleService.Core.Queries;
using PeopleService.WebApi.Auth;

namespace PeopleService.WebApi.Controllers
{
    /// <summary>
    /// Token endpoint for TraderX. Login is the only intentionally public (anonymous)
    /// operation in this service; everything else requires a bearer token.
    /// </summary>
    [ApiController]
    [Route("[controller]")]
    public class AuthController : ControllerBase
    {
        private readonly IMediator _mediator;
        private readonly TokenIssuer _tokenIssuer;
        private readonly ILogger<AuthController> _logger;

        /// <summary>Creates the controller.</summary>
        public AuthController(IMediator mediator, TokenIssuer tokenIssuer, ILogger<AuthController> logger)
        {
            _mediator = mediator;
            _tokenIssuer = tokenIssuer;
            _logger = logger;
        }

        /// <summary>Directory credentials.</summary>
        public class LoginRequest
        {
            /// <summary>Logon (user name) as held in the directory.</summary>
            [Required]
            public string LogonId { get; set; } = string.Empty;

            /// <summary>Password.</summary>
            [Required]
            public string Password { get; set; } = string.Empty;
        }

        /// <summary>Issued bearer token and the identity it carries.</summary>
        public class LoginResponse
        {
            /// <summary>Signed JWT to send as <c>Authorization: Bearer ...</c>.</summary>
            public string AccessToken { get; set; } = string.Empty;
            /// <summary>Always "Bearer".</summary>
            public string TokenType { get; set; } = "Bearer";
            /// <summary>UTC expiry of the token.</summary>
            public DateTime ExpiresAt { get; set; }
            /// <summary>Logon id of the authenticated user.</summary>
            public string LogonId { get; set; } = string.Empty;
            /// <summary>Display name of the authenticated user.</summary>
            public string? FullName { get; set; }
            /// <summary>Roles granted (USER, optionally ADMIN).</summary>
            public IReadOnlyList<string> Roles { get; set; } = Array.Empty<string>();
        }

        /// <summary>
        /// Exchange directory credentials for a TraderX bearer token.
        /// </summary>
        /// <response code="200">Credentials accepted; token issued</response>
        /// <response code="401">Unknown user or wrong password</response>
        [HttpPost(nameof(Login))]
        [AllowAnonymous]
        [ProducesResponseType(typeof(LoginResponse), 200)]
        [ProducesResponseType(401)]
        public async Task<IActionResult> Login([FromBody] LoginRequest request)
        {
            _logger.LogInformation("PeopleService.Auth.Login called for LogonId: {LogonId}", request.LogonId);

            var person = await _mediator.Send(new GetPerson.Request { LogonId = request.LogonId });
            if (person == null || !_tokenIssuer.PasswordMatches(request.Password))
            {
                _logger.LogWarning("Login rejected for LogonId: {LogonId}", request.LogonId);
                return Unauthorized();
            }

            var (token, expiresAt, roles) = _tokenIssuer.Issue(person);
            return Ok(new LoginResponse
            {
                AccessToken = token,
                ExpiresAt = expiresAt,
                LogonId = person.LogonId ?? request.LogonId,
                FullName = person.FullName,
                Roles = roles,
            });
        }

        /// <summary>
        /// Return the identity carried by the presented bearer token.
        /// </summary>
        [HttpGet(nameof(Me))]
        [Authorize]
        public IActionResult Me()
        {
            return Ok(new
            {
                logonId = User.Identity?.Name,
                roles = User.FindAll(TraderXAuthOptions.RolesClaim).Select(c => c.Value).ToArray(),
            });
        }
    }
}
