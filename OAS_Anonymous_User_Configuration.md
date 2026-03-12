# Oracle Analytics Server 2024 (7.6) – Anonymous User Configuration

## Overview

Oracle Analytics Server (OAS) 2024, version 7.6, supports **anonymous (guest) user access**, allowing unauthenticated users to view specific dashboards, reports, and analyses without logging in. This document provides step-by-step instructions for enabling and configuring anonymous user access on OAS 7.6.

> **Note:** Anonymous user access should be enabled only for content intended for public consumption. Sensitive or confidential data must never be exposed through anonymous access.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Architecture Overview](#architecture-overview)
3. [Enable Anonymous User Access in OAS](#1-enable-anonymous-user-access-in-oas)
4. [Configure the Anonymous User Account in Identity Store](#2-configure-the-anonymous-user-account-in-identity-store)
5. [Assign Application Role to the Anonymous User](#3-assign-application-role-to-the-anonymous-user)
6. [Grant Catalog Permissions to the Anonymous User](#4-grant-catalog-permissions-to-the-anonymous-user)
7. [Configure WebLogic Server for Anonymous Authentication](#5-configure-weblogic-server-for-anonymous-authentication)
8. [Set Up the Anonymous Logon URL](#6-set-up-the-anonymous-logon-url)
9. [Restrict Data Model Access for Anonymous Users](#7-restrict-data-model-access-for-anonymous-users)
10. [Security Considerations](#security-considerations)
11. [Verification Steps](#verification-steps)
12. [Troubleshooting](#troubleshooting)

---

## Prerequisites

Before configuring anonymous user access, ensure the following:

| Requirement | Details |
|---|---|
| OAS Version | Oracle Analytics Server 2024 (7.6) |
| Administrator Access | Full OAS system administrator credentials |
| WebLogic Admin Access | Access to WebLogic Server Administration Console |
| Identity Store | Embedded LDAP, Microsoft Active Directory, or Oracle Internet Directory configured |
| Existing Deployment | OAS is fully installed and operational |

---

## Architecture Overview

In OAS 7.6, anonymous user access works through the following flow:

```
Browser (unauthenticated user)
        |
        v
OAS Presentation Server
        |
        v
Anonymous User Identity (mapped in WebLogic/Identity Store)
        |
        v
Application Role: BIConsumer (or custom restricted role)
        |
        v
Catalog Folder (read-only permissions granted to anonymous user)
        |
        v
Subject Area / Data Model (read access granted)
```

---

## 1. Enable Anonymous User Access in OAS

Anonymous access is controlled through the OAS system configuration file (`instanceconfig.xml`) and must be explicitly enabled.

### 1.1 Locate the Configuration File

The `instanceconfig.xml` file is located at:

```
$ORACLE_INSTANCE/config/OracleBIPresentationServicesComponent/coreapplication_obips1/instanceconfig.xml
```

> Replace `$ORACLE_INSTANCE` with your actual Oracle instance home directory, typically:
> `/u01/data/domains/bi/config/fmwconfig/bienv/core/`

### 1.2 Edit `instanceconfig.xml`

Open the file with a text editor (as the `oracle` OS user) and add or update the `<Security>` section:

```xml
<Security>
  <AnonymousUser>
    <enable>true</enable>
    <logon>true</logon>
    <UserName>oas_anonymous</UserName>
  </AnonymousUser>
</Security>
```

**Configuration Parameters:**

| Parameter | Value | Description |
|---|---|---|
| `enable` | `true` | Enables anonymous user feature in OAS |
| `logon` | `true` | Allows anonymous users to access OAS without authentication |
| `UserName` | `oas_anonymous` | The identity store username mapped as the anonymous user |

### 1.3 Restart the OAS Presentation Services

After modifying `instanceconfig.xml`, restart the OAS Presentation Services component:

```bash
# Stop OAS services
$ORACLE_HOME/user_projects/domains/bi/bitools/bin/stop.sh

# Start OAS services
$ORACLE_HOME/user_projects/domains/bi/bitools/bin/start.sh
```

Or use the Fusion Middleware Control console to restart the `coreapplication_obips1` component.

---

## 2. Configure the Anonymous User Account in Identity Store

The anonymous user must exist as a real user account in your identity store.

### 2.1 Using Embedded LDAP (Default)

1. Navigate to the **WebLogic Server Administration Console**:
   `http://<hostname>:9500/console`

2. Go to **Security Realms → myrealm → Users and Groups → Users**.

3. Click **New** and create a user with the following attributes:

   | Field | Value |
   |---|---|
   | Name | `oas_anonymous` |
   | Description | OAS Anonymous User (read-only guest account) |
   | Password | A strong, secure password (internal use only; users will not log in with it) |
   | Provider | DefaultAuthenticator |

4. Click **OK** to save.

### 2.2 Using Active Directory or OID

If using an external identity store, create the user `oas_anonymous` in your directory (AD/OID) with a secure, complex password and ensure the user is synchronized with OAS via the configured identity store provider.

---

## 3. Assign Application Role to the Anonymous User

The anonymous user requires an OAS application role to access content.

### 3.1 Open Fusion Middleware Control

1. Navigate to:
   `http://<hostname>:9500/em`

2. Log in with your administrator credentials.

3. Expand **WebLogic Domain → bifoundation_domain**.

4. Right-click on **bifoundation_domain** and select **Security → Application Roles**.

5. Select the application **`obi`** from the drop-down.

### 3.2 Assign Role to the Anonymous User

1. Search for and select the role **`BIConsumer`** (or a custom, more restrictive role created for anonymous access).

2. Click **Edit**.

3. Under **Members**, click **Add**.

4. Select **User** as the type, search for `oas_anonymous`, and click **OK**.

5. Click **OK** again to save the role assignment.

> **Best Practice:** Create a dedicated, minimal application role (e.g., `BIAnonymous`) with only the permissions required for anonymous content rather than using the standard `BIConsumer` role.

---

## 4. Grant Catalog Permissions to the Anonymous User

Anonymous users can only view catalog objects (dashboards, analyses, reports) that they have explicit read permissions on.

### 4.1 Access the Catalog Manager

1. Log in to OAS as an Administrator:
   `http://<hostname>:9502/analytics`

2. Navigate to **Catalog** in the top menu.

3. Browse to the folder containing the dashboards/reports intended for anonymous access.

### 4.2 Set Folder Permissions

1. Right-click the target folder and select **Permissions**.

2. Click **Add** to add a new permission entry.

3. In the **Select User / Role / Group** dialog:
   - Select **User**
   - Search for and select `oas_anonymous`

4. Set the **Permission Level** to:

   | Permission | Recommended Setting |
   |---|---|
   | Open/Run | ✅ Allow |
   | View | ✅ Allow |
   | Modify | ❌ Deny |
   | Delete | ❌ Deny |
   | Change Permissions | ❌ Deny |
   | Full Control | ❌ Deny |

5. Click **OK** to apply permissions.

6. Optionally, check **Apply permissions recursively** to propagate to all sub-folders and objects.

### 4.3 Verify Individual Object Permissions

Confirm that dashboards/analyses within the folder also have appropriate permissions for `oas_anonymous` by right-clicking individual objects and checking their **Permissions**.

---

## 5. Configure WebLogic Server for Anonymous Authentication

By default, WebLogic may block unauthenticated requests. Configure it to allow anonymous pass-through.

### 5.1 Enable the Anonymous Authentication Provider (If Required)

1. Log in to the WebLogic Server Administration Console:
   `http://<hostname>:9500/console`

2. Go to **Security Realms → myrealm → Providers → Authentication**.

3. Click **New** to add a new Authentication Provider.

4. Select **AnonymousAuthenticator** from the provider type list and provide a name (e.g., `OASAnonymousAuthenticator`).

5. Click **OK**.

6. Set the control flag for the new provider to **SUFFICIENT**.

7. Reorder the providers so **AnonymousAuthenticator** appears after the primary authenticator (e.g., `DefaultAuthenticator`).

8. Click **Save** and **Activate Changes**.

9. Restart the managed servers when prompted.

### 5.2 Configure the Identity Asserter (Optional – SSO Environments)

If OAS is deployed behind a reverse proxy or SSO system, ensure the anonymous user identity assertion is configured in the **IdentityAssertion** provider to map anonymous requests to the `oas_anonymous` user.

---

## 6. Set Up the Anonymous Logon URL

OAS provides a direct URL that bypasses the standard login page and logs in as the anonymous user.

### 6.1 Standard Anonymous Access URL Format

The anonymous access URL follows this pattern:

```
http://<hostname>:9502/analytics/saw.dll?dashboard&PortalPath=/shared/<FolderName>/<DashboardName>&Page=<PageName>&NQUser=oas_anonymous&NQPassword=<password>
```

> **Important:** Embedding credentials in a URL is not recommended for production environments. Use the embedded anonymous authentication flow instead (via `instanceconfig.xml`) to avoid exposing credentials.

### 6.2 Recommended Anonymous Access URL (Token-Based)

For a cleaner and more secure anonymous access experience, use the guest redirect capability configured via `instanceconfig.xml` (as set up in Step 1). Users can then access content directly:

```
http://<hostname>:9502/analytics/saw.dll?portalgo&PortalPath=/shared/Public/MyDashboard
```

If `instanceconfig.xml` is properly configured with `<logon>true</logon>`, OAS will automatically authenticate the request as `oas_anonymous` without requiring credentials in the URL.

---

## 7. Restrict Data Model Access for Anonymous Users

In addition to catalog permissions, ensure that the underlying data models (Subject Areas / RPD) are restricted for the anonymous user.

### 7.1 Using OAS Data Modeler or RPD (Offline Mode)

1. Open the **Oracle BI Administration Tool** (on Windows) or connect to the RPD online.

2. Navigate to **Manage → Identity** and ensure `oas_anonymous` is visible.

3. For each **Subject Area**, right-click and select **Permissions**.

4. Explicitly set **Read** access for `oas_anonymous` (or its assigned application role) only for subject areas required for anonymous dashboards.

5. Set all other subject areas to **No Access** for the anonymous user.

6. Save and deploy the updated RPD.

### 7.2 Using Data Sets (OAS Self-Service)

For self-service workbooks and datasets:
- Assign **Viewer** access only to datasets required for anonymous dashboards.
- Use **Workbook Sharing** to explicitly share workbooks with the `oas_anonymous` user.

---

## Security Considerations

| Consideration | Recommendation |
|---|---|
| **Least Privilege** | Grant anonymous user only the minimum permissions (Read/Run) needed to access specific content |
| **No PII Exposure** | Ensure no personally identifiable information (PII) or sensitive financial data is included in anonymous dashboards |
| **Data Filters** | Apply row-level security / data filters on subject areas to limit the data visible to the anonymous user |
| **Audit Logging** | Enable OAS audit logging to track access by the anonymous user account |
| **Password Rotation** | Even though the password is not directly used by end users, rotate the `oas_anonymous` account password periodically |
| **Network Security** | Restrict anonymous access to internal networks or specific IP ranges using WebLogic connection filters if needed |
| **HTTPS** | Ensure OAS is served over HTTPS to protect data in transit even for anonymous content |
| **Session Timeout** | Configure appropriate session timeout for anonymous sessions in `instanceconfig.xml` |

---

## Verification Steps

After completing the configuration, verify anonymous user access:

1. **Test Anonymous Access via Browser:**
   - Open an **incognito/private browser window** (to avoid using cached credentials).
   - Navigate to the anonymous access URL configured in Step 6.
   - Verify that the dashboard loads without a login prompt.

2. **Verify User Identity:**
   - If you have access to OAS session logs, confirm that active sessions show `oas_anonymous` as the user.
   - Navigate to **Administration → Manage Sessions** in OAS and confirm the anonymous user session.

3. **Test Permission Boundaries:**
   - Attempt to access a folder or dashboard that `oas_anonymous` does NOT have permissions to.
   - Verify that access is denied with an appropriate error message (not a login prompt).

4. **Check Audit Logs:**
   - Review OAS usage tracking or audit logs to confirm that access events are attributed to `oas_anonymous`.

5. **Validate from Fusion Middleware Control:**
   - Log in to `http://<hostname>:9500/em`.
   - Navigate to **Business Intelligence → coreapplication → Diagnostics → Log Messages**.
   - Confirm there are no authentication errors for the anonymous user.

---

## Troubleshooting

| Issue | Possible Cause | Resolution |
|---|---|---|
| Anonymous access redirects to login page | `instanceconfig.xml` not updated or not restarted | Verify the `<AnonymousUser>` section in `instanceconfig.xml` and restart Presentation Services |
| "Account does not exist" error | `oas_anonymous` user not created in identity store | Create the user in WebLogic Embedded LDAP or external directory (see Step 2) |
| "Insufficient privileges" error | Application role not assigned to `oas_anonymous` | Assign `BIConsumer` or a custom role to the anonymous user (see Step 3) |
| Catalog objects not visible | Folder/object permissions not set for `oas_anonymous` | Grant read permissions on catalog objects (see Step 4) |
| OAS shows login prompt even with correct URL | `<logon>true</logon>` not set in `instanceconfig.xml` | Update `instanceconfig.xml` and restart |
| Anonymous user can see restricted dashboards | Permissions applied too broadly | Review and restrict catalog permissions; verify data-level security filters |
| WebLogic blocking anonymous requests | Anonymous Authentication Provider not configured | Add and configure `AnonymousAuthenticator` in WebLogic (see Step 5) |
| Dashboard loads but data is missing | Subject area permissions not granted | Grant read access to required subject areas in the RPD (see Step 7) |

---

## References

- [Oracle Analytics Server 2024 Documentation](https://docs.oracle.com/en/middleware/bi/analytics-server/index.html)
- [Oracle Analytics Server Security Guide 7.6](https://docs.oracle.com/en/middleware/bi/analytics-server/user-guide-security/index.html)
- [Oracle Fusion Middleware Administration Guide for OAS](https://docs.oracle.com/en/middleware/bi/analytics-server/administer/index.html)
- [WebLogic Server Security Providers Documentation](https://docs.oracle.com/en/middleware/fusion-middleware/weblogic-server/index.html)

---

*Document Version: 1.0 | Applicable OAS Version: 2024 (7.6) | Last Updated: March 2026*
