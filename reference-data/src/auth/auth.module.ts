import { Module } from '@nestjs/common';
import { APP_GUARD } from '@nestjs/core';
import { JwtModule } from '@nestjs/jwt';
import { JwtAuthGuard } from './jwt-auth.guard';

export function loadAuthSecret(env: NodeJS.ProcessEnv = process.env): string {
  const secret = env.TRADERX_AUTH_SECRET;
  if (!secret || Buffer.byteLength(secret, 'utf8') < 32) {
    throw new Error(
      'TRADERX_AUTH_SECRET must be set and at least 32 bytes long',
    );
  }
  return secret;
}

@Module({
  imports: [
    JwtModule.registerAsync({
      useFactory: () => ({ secret: loadAuthSecret() }),
    }),
  ],
  providers: [{ provide: APP_GUARD, useClass: JwtAuthGuard }],
})
export class AuthModule {}
