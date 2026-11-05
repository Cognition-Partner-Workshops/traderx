import { Controller, Get } from '@nestjs/common';
import { HealthCheckService, HealthCheck } from '@nestjs/terminus';
import { Public } from '../auth/public.decorator';

/** Liveness probe: explicitly public so orchestrators can poll it without credentials. */
@Public()
@Controller('health')
class HealthController {
    constructor(private health: HealthCheckService) {}

    @Get()
    @HealthCheck()
    check() {
        return this.health.check([]);
    }
}
export default HealthController;