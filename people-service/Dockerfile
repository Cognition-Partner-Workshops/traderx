FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /people-service
COPY . .
RUN dotnet publish PeopleService.WebApi/PeopleService.WebApi.csproj -c Release -o /app/publish

FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /people-service
COPY --from=build /app/publish .
COPY --from=build /people-service/PeopleService.WebApi/MockDirectory ./MockDirectory
ENV ASPNETCORE_URLS=http://0.0.0.0:18089
ENV ASPNETCORE_ENVIRONMENT=Development
EXPOSE 18089
ENTRYPOINT ["dotnet", "PeopleService.WebApi.dll"]