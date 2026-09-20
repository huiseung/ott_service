FROM node:22-alpine AS deps
WORKDIR /app
RUN corepack enable
COPY apps/user-web/package.json apps/user-web/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile --config.node-linker=hoisted

FROM node:22-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY apps/user-web/ ./
ARG NEXT_PUBLIC_USER_API_BASE_URL=http://localhost:8081
ENV NEXT_PUBLIC_USER_API_BASE_URL=$NEXT_PUBLIC_USER_API_BASE_URL
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
