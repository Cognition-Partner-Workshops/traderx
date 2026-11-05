import {
  CanActivate,
  ExecutionContext,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { JwtService } from '@nestjs/jwt';
import { Request } from 'express';
import { IS_PUBLIC_KEY } from './public.decorator';

export const TRADERX_ISSUER = 'traderx';

export interface CallerIdentity {
  subject: string;
  roles: string[];
}

/**
 * Global guard: every route requires a valid HS256 TraderX bearer token unless
 * it is explicitly marked with @Public().
 */
@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    private readonly jwtService: JwtService,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    if (isPublic) {
      return true;
    }

    const request = context
      .switchToHttp()
      .getRequest<Request & { user?: CallerIdentity }>();
    const token = JwtAuthGuard.extractBearerToken(request);
    if (!token) {
      throw new UnauthorizedException('Missing bearer token');
    }
    try {
      const claims = await this.jwtService.verifyAsync<{
        sub: string;
        roles?: string[];
      }>(token, {
        algorithms: ['HS256'],
        issuer: TRADERX_ISSUER,
      });
      request.user = {
        subject: claims.sub,
        roles: Array.isArray(claims.roles)
          ? claims.roles.map((r) => String(r).toUpperCase())
          : [],
      };
      return true;
    } catch {
      throw new UnauthorizedException('Invalid bearer token');
    }
  }

  private static extractBearerToken(request: Request): string | undefined {
    const header = request.headers.authorization;
    if (!header) {
      return undefined;
    }
    const [scheme, token] = header.split(' ');
    return scheme?.toLowerCase() === 'bearer' && token ? token : undefined;
  }
}
