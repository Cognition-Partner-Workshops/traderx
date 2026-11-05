FROM nginx

EXPOSE 8080
ARG NGINX_HOST="localhost"
ENV NGINX_HOST=$NGINX_HOST

# Content-Security-Policy sent on every response. The default fits the Angular app:
# no inline/eval scripts, inline styles (Angular/ag-grid), and API/socket.io calls to
# other ports on the same host (environment.ts uses //<host>:<port>). Override per environment.
ARG NGINX_CSP="default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self' http: https: ws: wss:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"
ENV NGINX_CSP=$NGINX_CSP

# This is a workaround for the dollar sign in the envsubst command
ARG DOLLAR="$"
ENV DOLLAR=$DOLLAR

COPY nginx.traderx.conf.template /etc/nginx/conf.d/nginx.traderx.conf.template

RUN envsubst < /etc/nginx/conf.d/nginx.traderx.conf.template > /etc/nginx/conf.d/default.conf
