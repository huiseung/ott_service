FROM node:22-alpine AS deps
WORKDIR /app
RUN corepack enable
COPY apps/admin-web/package.json apps/admin-web/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile --config.node-linker=hoisted

FROM node:22-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY apps/admin-web/ ./
ARG NEXT_PUBLIC_ADMIN_API_BASE_URL=http://localhost:8080
ENV NEXT_PUBLIC_ADMIN_API_BASE_URL=$NEXT_PUBLIC_ADMIN_API_BASE_URL
ARG ADMIN_API_INTERNAL_URL=http://localhost:8080
ENV ADMIN_API_INTERNAL_URL=$ADMIN_API_INTERNAL_URL
RUN corepack enable && pnpm build

FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
COPY --from=builder /app/public ./public
USER node
EXPOSE 3000
CMD ["node", "server.js"]
