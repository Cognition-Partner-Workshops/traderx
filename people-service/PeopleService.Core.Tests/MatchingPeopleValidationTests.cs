using PeopleService.Core.Queries;
using Xunit;

namespace PeopleService.Core.Tests;

public class MatchingPeopleValidationTests
{
    private readonly GetMatchingPeople.RequestValidator _validator = new();

    [Theory]
    [InlineData(0)]
    [InlineData(-1)]
    [InlineData(101)]
    [InlineData(int.MaxValue)]
    public void RejectsOutOfRangeTake(int take)
    {
        var result = _validator.Validate(new GetMatchingPeople.Request { SearchText = "user", Take = take });
        var error = Assert.Single(result.Errors);
        Assert.Equal("Take", error.PropertyName);
        Assert.Equal("Take must be between 1 and 100", error.ErrorMessage);
    }

    [Theory]
    [InlineData(1)]
    [InlineData(10)]
    [InlineData(100)]
    public void AcceptsInclusiveTakeRange(int take)
    {
        Assert.True(_validator.Validate(new GetMatchingPeople.Request { SearchText = "user", Take = take }).IsValid);
    }

    [Theory]
    [InlineData(null, "SearchText must be provided")]
    [InlineData("", "SearchText must be provided")]
    [InlineData(" ", "SearchText must be provided")]
    [InlineData("   ", "SearchText must be provided")]
    [InlineData("a", "SearchText must be at least 3 characters long")]
    [InlineData("ab", "SearchText must be at least 3 characters long")]
    public void PreservesSearchTextValidation(string? text, string message)
    {
        var result = _validator.Validate(new GetMatchingPeople.Request { SearchText = text });
        Assert.Equal(message, Assert.Single(result.Errors).ErrorMessage);
    }

    [Fact]
    public void AcceptsThreeCharacterSearchAndDefaultTake()
    {
        Assert.True(_validator.Validate(new GetMatchingPeople.Request { SearchText = "use" }).IsValid);
    }

    [Fact]
    public async Task AsyncValidationAlsoRejectsInvalidTake()
    {
        var result = await _validator.ValidateAsync(new GetMatchingPeople.Request { SearchText = "user", Take = 0 });
        Assert.False(result.IsValid);
    }
}
