using FluentValidation.AspNetCore;
using Microsoft.OpenApi.Models;
using PeopleService.Core.Infrastructure;
using Serilog;
using System.Reflection;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container.

builder.Services.AddControllers();
// Learn more about configuring Swagger/OpenAPI at https://aka.ms/aspnetcore/swashbuckle
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(
                c =>
                {
                    c.SwaggerDoc("v1", new OpenApiInfo { Title = "PeopleService.WebApi", Version = "v1" });
                    var xmlFilename = $"{Assembly.GetExecutingAssembly().GetName().Name}.xml";
                    c.CustomSchemaIds(t => t.FullName);
                    c.IncludeXmlComments(Path.Combine(AppContext.BaseDirectory, xmlFilename));
                });
builder.Services.AddMediatR(cfg => cfg.RegisterServicesFromAssembly(typeof(Program).Assembly));
builder.Services.AddPeopleServiceCore(builder.Configuration.GetSection("PeopleJsonFilePath"));
builder.Services.AddFluentValidationAutoValidation();
builder.Services.AddFluentValidationClientsideAdapters();

builder.Host.UseSerilog((hostContext, services, configuration) =>
{
    configuration.WriteTo.Console();
    configuration.WriteTo.RollingFile("Logs/PeopleService.log");
});

var app = builder.Build();

// Configure the HTTP request pipeline.
// Swagger is opt-in (Swagger__Enabled=true): the container runs with ASPNETCORE_ENVIRONMENT=Development via launchSettings.
if (app.Configuration.GetValue<bool>("Swagger:Enabled"))
{
    app.UseSwagger();
    app.UseSwaggerUI(c => c.SwaggerEndpoint("/swagger/v1/swagger.json", "PeopleService.WebApi v1"));
}

// Served over plain HTTP behind the ingress; enable (HttpsRedirection__Enabled=true) only when Kestrel/ingress serves HTTPS.
if (app.Configuration.GetValue<bool>("HttpsRedirection:Enabled"))
{
    app.UseHttpsRedirection();
}

app.UseAuthorization();

app.MapControllers();

app.UseCors(builder => builder
    .AllowAnyOrigin()
    .AllowAnyMethod()
    .AllowAnyHeader());

app.Run();
