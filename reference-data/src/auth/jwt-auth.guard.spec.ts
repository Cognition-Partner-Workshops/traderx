import { ExecutionContext, UnauthorizedException } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { JwtService } from '@nestjs/jwt';
import { JwtAuthGuard } from './jwt-auth.guard';
import { IS_PUBLIC_KEY } from './public.decorator';

const secret = 'unit-test-secret-that-is-at-least-32-bytes';

function contextFor(
  authorization?: string,
  isPublic = false,
): ExecutionContext {
  const request: { headers: Record<string, string>; user?: unknown } = {
    headers: authorization ? { authorization } : {},
  };
  const reflector = {
    getAllAndOverride: (key: string) =>
      key === IS_PUBLIC_KEY ? isPublic : undefined,
  } as unknown as Reflector;
  const context = {
    getHandler: () => ({}),
    getClass: () => ({}),
    switchToHttp: () => ({ getRequest: () => request }),
  } as unknown as ExecutionContext;
  return Object.assign(context, { reflector, request });
}

describe('JwtAuthGuard', () => {
  const jwtService = new JwtService({ secret });
  const buildGuard = (ctx: ExecutionContext & { reflector?: Reflector }) =>
    new JwtAuthGuard(ctx.reflector as Reflector, jwtService);

  it('allows @Public() routes without a token', async () => {
    const ctx = contextFor(undefined, true);
    await expect(buildGuard(ctx).canActivate(ctx)).resolves.toBe(true);
  });

  it('rejects a request without a bearer token', async () => {
    const ctx = contextFor(undefined);
    await expect(buildGuard(ctx).canActivate(ctx)).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
  });

  it('rejects a token signed with another secret', async () => {
    const forged = new JwtService({
      secret: 'another-secret-that-is-also-32-bytes-long',
    }).sign({ sub: 'mallory', roles: ['ADMIN'] }, { issuer: 'traderx' });
    const ctx = contextFor(`Bearer ${forged}`);
    await expect(buildGuard(ctx).canActivate(ctx)).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
  });

  it('accepts a valid token and exposes the caller identity', async () => {
    const token = jwtService.sign(
      { sub: 'user01', roles: ['user'] },
      { issuer: 'traderx', expiresIn: '5m' },
    );
    const ctx = contextFor(`Bearer ${token}`) as ExecutionContext & {
      request: { user?: unknown };
    };
    await expect(buildGuard(ctx).canActivate(ctx)).resolves.toBe(true);
    expect(ctx.request.user).toEqual({ subject: 'user01', roles: ['USER'] });
  });
});
