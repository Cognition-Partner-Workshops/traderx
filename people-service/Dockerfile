FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY . .
RUN dotnet publish PeopleService.WebApi -c Release -o /app

FROM mcr.microsoft.com/dotnet/aspnet:8.0
WORKDIR /people-service
RUN mkdir -p Logs && chown app:app Logs
COPY --from=build --chown=app:app /app ./
COPY --from=build --chown=app:app /src/PeopleService.WebApi/MockDirectory ./MockDirectory
ENV ASPNETCORE_URLS=http://0.0.0.0:18089
EXPOSE 18089
USER app
ENTRYPOINT ["dotnet","PeopleService.WebApi.dll"]