import { SetMetadata } from '@nestjs/common';

export const IS_PUBLIC_KEY = 'isPublic';

/** Explicitly designates a route as public (no caller identity required). */
export const Public = () => SetMetadata(IS_PUBLIC_KEY, true);
