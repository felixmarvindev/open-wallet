# Keycloak Setup Guide

This guide explains how to set up Keycloak for the OpenWallet microservices platform.

## Prerequisites

- Keycloak running via Docker Compose (already configured)
- Access to Keycloak Admin Console at `http://localhost:8080`

## Step 1: Access Keycloak Admin Console

1. Start Keycloak (if not already running):
   ```bash
   docker-compose up -d keycloak
   ```

2. Wait for Keycloak to be ready (check health):
   ```bash
   curl http://localhost:8080/health/ready
   ```

3. Access Admin Console:
   - URL: `http://localhost:8080`
   - Username: `admin`
   - Password: `admin`

## Step 2: Create Realm

1. In Keycloak Admin Console, click **"Create Realm"** (top left)
2. Enter realm name: `openwallet`
3. Click **"Create"**
4. The realm is now active (you should see "openwallet" in the top-left dropdown)

## Step 3: Create Client for Auth Service

1. In the left sidebar, go to **"Clients"**
2. Click **"Create client"**
3. Configure the client:
   - **Client type**: `OpenID Connect`
   - **Client ID**: `auth-service`
   - Click **"Next"**
4. Configure capabilities:
   - **Client authentication**: `ON` (confidential client)
   - **Authorization**: `OFF`
   - **Authentication flow**: `Standard flow`
   - Click **"Next"**
5. Configure login settings:
   - **Root URL**: Leave empty
   - **Home URL**: Leave empty
   - **Valid redirect URIs**: Leave empty (not needed for service-to-service)
   - **Web origins**: Leave empty
   - Click **"Save"**
6. After saving, go to the **"Credentials"** tab
7. Copy the **"Client secret"** (you'll need this for `application-local.yml`)

## Step 4: Create Realm Roles

1. In the left sidebar, go to **"Realm roles"**
2. Click **"Create role"** and create the following roles:
   - **Role name**: `USER`
     - Description: `Default role for registered users`
   - **Role name**: `ADMIN`
     - Description: `Administrator role with full access`
   - **Role name**: `AUDITOR`
     - Description: `Auditor role with read-only access`

## Step 5: Configure Token Settings (Optional)

1. In the left sidebar, go to **"Realm settings"**
2. Go to the **"Tokens"** tab
3. Configure token lifetimes (recommended):
   - **Access Token Lifespan**: `15 minutes` (900 seconds)
   - **SSO Session Idle**: `30 minutes` (1800 seconds)
   - **SSO Session Max**: `10 hours` (36000 seconds)
   - **Access Token Lifespan For Implicit Flow**: `15 minutes` (900 seconds)
   - **Client Login Timeout**: `1 minute` (60 seconds)
   - **Login Timeout**: `5 minutes` (300 seconds)
4. Click **"Save"**

## Step 6: Configure Client Secret in Application

1. Update `auth-service/src/main/resources/application-local.yml`:
   ```yaml
   keycloak:
     server-url: http://localhost:8080
     realm: openwallet
     client-id: auth-service
     client-secret: <paste-client-secret-here>
     admin-username: admin
     admin-password: admin
   ```

   Or set via environment variable:
   ```bash
   export KEYCLOAK_CLIENT_SECRET=<paste-client-secret-here>
   ```

## Step 7: Verify Setup

### Test Admin Client Connection

1. Start the auth-service:
   ```bash
   cd auth-service
   mvn spring-boot:run
   ```

2. Check logs for Keycloak connection errors

### Test User Creation (via API - after Phase 2 implementation)

```bash
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "Test123!@#"
  }'
```

### Test Token Endpoint (Direct)

```bash
curl -X POST http://localhost:8080/realms/openwallet/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=auth-service" \
  -d "client_secret=<your-client-secret>" \
  -d "username=testuser" \
  -d "password=Test123!@#"
```

## Troubleshooting

### Issue: "Client not found" error

**Solution**: Ensure the client `auth-service` is created in the `openwallet` realm with client authentication enabled.

### Issue: "Invalid client credentials" error

**Solution**: 
1. Verify the client secret in `application-local.yml` matches the one in Keycloak
2. Check the client has "Client authentication" enabled
3. Regenerate the client secret if needed

### Issue: "User already exists" error

**Solution**: Delete the user from Keycloak Admin Console:
1. Go to **"Users"** in the left sidebar
2. Search for the user
3. Click on the user
4. Click **"Delete"**

### Issue: "Role not found" error

**Solution**: Ensure the `USER` role exists in the realm:
1. Go to **"Realm roles"**
2. Verify `USER` role exists
3. Create it if missing

## Manual Role Assignment

To assign ADMIN or AUDITOR roles to a user:

1. Go to **"Users"** in Keycloak Admin Console
2. Search for and click on the user
3. Go to the **"Role mapping"** tab
4. Click **"Assign role"**
5. Filter by realm roles
6. Select `ADMIN` or `AUDITOR`
7. Click **"Assign"**

## Production Considerations

For production environments:

1. **Change default admin password** in Keycloak
2. **Use environment variables** for sensitive configuration:
   ```yaml
   keycloak:
     client-secret: ${KEYCLOAK_CLIENT_SECRET}
     admin-username: ${KEYCLOAK_ADMIN_USERNAME}
     admin-password: ${KEYCLOAK_ADMIN_PASSWORD}
   ```
3. **Use HTTPS** for Keycloak server URL
4. **Configure proper token lifetimes** based on security requirements
5. **Enable Keycloak clustering** for high availability
6. **Set up proper backup strategy** for Keycloak database

## Additional Resources

- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Keycloak Admin REST API](https://www.keycloak.org/docs-api/latest/rest-api/)
- [OAuth2 Token Endpoint](https://www.keycloak.org/docs/latest/securing_apps/#_token-endpoint)

