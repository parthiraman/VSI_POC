# Oracle Analytics Server 8.0 – Anonymous User Configuration

## Overview

Oracle Analytics Server (OAS) 8.0 supports **anonymous (guest) user access**, allowing unauthenticated users to view specific dashboards, reports, and analyses without logging in. This document provides step-by-step instructions for enabling and configuring anonymous user access on OAS 8.0.

OAS 8.0 introduces enhancements to the security and administration layer compared to earlier releases. Key changes include an updated Fusion Middleware Control interface, support for OCI IAM (Oracle Cloud Infrastructure Identity and Access Management) as an identity provider, and a streamlined REST-based administration API alongside the traditional WebLogic-based configuration.

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
| OAS Version | Oracle Analytics Server 8.0 |
| Administrator Access | Full OAS system administrator credentials |
| WebLogic Admin Access | Access to WebLogic Server Administration Console (12.2.1.4.0 or later bundled with OAS 8.0) |
| Identity Store | Embedded LDAP, Microsoft Active Directory, Oracle Internet Directory, or OCI IAM configured |
| Existing Deployment | OAS 8.0 is fully installed, patched to the latest Bundle Patch, and operational |
| JDK | JDK 11 or JDK 17 as required by OAS 8.0 |

---

## Architecture Overview

In OAS 8.0, anonymous user access works through the following flow:

```
Browser (unauthenticated user)
        |
        v
OAS Presentation Server (OAS 8.0)
        |
        v
Anonymous User Identity (mapped in WebLogic / OCI IAM / Identity Store)
        |
        v
Application Role: BIConsumer (or custom restricted role)
        |
        v
Catalog Folder (read-only permissions granted to anonymous user)
        |
        v
Subject Area / Semantic Model (read access granted)
```

> **OAS 8.0 Note:** OAS 8.0 replaces the term "RPD / Data Model" with **Semantic Model** in the user interface and documentation. Configuration steps referencing the data model use the updated terminology.

---

## 1. Enable Anonymous User Access in OAS

Anonymous access is controlled through the OAS system configuration file (`instanceconfig.xml`) and must be explicitly enabled.

### 1.1 Locate the Configuration File

The `instanceconfig.xml` file is located at:

```
$ORACLE_INSTANCE/config/OracleBIPresentationServicesComponent/coreapplication_obips1/instanceconfig.xml
```

> Replace `$ORACLE_INSTANCE` with your actual Oracle instance home directory. On a typical OAS 8.0 on-premises installation this is:
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
| `enable` | `true` | Enables the anonymous user feature in OAS |
| `logon` | `true` | Allows anonymous users to access OAS without authentication |
| `UserName` | `oas_anonymous` | The identity store username mapped as the anonymous user |

> **OAS 8.0 Note:** OAS 8.0 supports an optional `<SessionTimeoutMinutes>` sub-element inside `<AnonymousUser>` to set a dedicated session timeout for anonymous sessions independently of authenticated user sessions. Example:
>
> ```xml
> <AnonymousUser>
>   <enable>true</enable>
>   <logon>true</logon>
>   <UserName>oas_anonymous</UserName>
>   <SessionTimeoutMinutes>30</SessionTimeoutMinutes>
> </AnonymousUser>
> ```

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
   | Password | A strong, secure password (internal use only; end users will not log in with this password) |
   | Provider | DefaultAuthenticator |

4. Click **OK** to save.

### 2.2 Using Active Directory or Oracle Internet Directory (OID)

If using an external identity store, create the user `oas_anonymous` in your directory (AD/OID) with a secure, complex password and ensure the user is synchronized with OAS via the configured identity store provider.

### 2.3 Using OCI IAM (New in OAS 8.0)

OAS 8.0 introduced native integration with **Oracle Cloud Infrastructure Identity and Access Management (OCI IAM)**. If your OAS 8.0 deployment uses OCI IAM as the identity provider:

1. Log in to the **OCI Console** (`https://cloud.oracle.com`).
2. Navigate to **Identity & Security → Identity → Users**.
3. Click **Create User** and create a user with the following attributes:
   - **Name:** `oas_anonymous`
   - **Description:** OAS Anonymous User (read-only guest account)
   - **Email:** A valid internal email address (required by OCI IAM)
4. Assign the user to the OCI IAM group that is mapped to the OAS `BIConsumer` application role (see Step 3).
5. In OAS Fusion Middleware Control, verify that the OCI IAM identity provider is correctly federated and that user `oas_anonymous` is visible in OAS.

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

> **Best Practice:** Create a dedicated, minimal application role (e.g., `BIAnonymous`) with only the permissions required for anonymous content rather than using the standard `BIConsumer` role. In OAS 8.0, you can create and manage custom application roles directly in the **OAS Administration Console** under **Console → Security → Application Roles**, in addition to the Fusion Middleware Control.

### 3.3 Using the OAS REST Administration API (OAS 8.0)

OAS 8.0 provides a REST API for managing application roles and user assignments. As an alternative to the UI, use the following API call to add `oas_anonymous` to a role:

```bash
curl -X POST \
  "http://<hostname>:9502/api/20210901/applicationRoles/BIConsumer/members" \
  -H "Content-Type: application/json" \
  -u <admin_user>:<admin_password> \
  -d '{"type": "user", "name": "oas_anonymous"}'
```

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

If OAS 8.0 is deployed behind a reverse proxy or an SSO system (e.g., Oracle Access Manager, or OCI IAM-based federation), ensure the anonymous user identity assertion is configured in the **IdentityAssertion** provider to map anonymous requests to the `oas_anonymous` user.

> **OAS 8.0 Note:** When using **OCI IAM** as the identity provider, anonymous user pass-through is handled through the OCI IAM federation configuration. Consult the [OAS 8.0 Security Guide](#references) for federation-specific anonymous access configuration.

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

### 6.3 Direct Workbook URL (OAS 8.0 Self-Service)

OAS 8.0 supports direct anonymous access to self-service **Workbooks** (DV projects) via the following URL pattern:

```
http://<hostname>:9502/ui/dv/ui/home.jsp#action=openProject&projectId=<workbook_id>
```

Ensure the target Workbook is shared with the `oas_anonymous` user (or with the `BIAnonymous` application role) at the **Viewer** permission level.

---

## 7. Restrict Data Model Access for Anonymous Users

In addition to catalog permissions, ensure that the underlying data models (Semantic Models) are restricted for the anonymous user.

### 7.1 Using the OAS Semantic Modeler (OAS 8.0)

OAS 8.0 introduces the browser-based **Semantic Modeler** as the replacement for the desktop-based Oracle BI Administration Tool. To restrict Semantic Model (formerly RPD) access:

1. Log in to OAS as an Administrator and navigate to:
   `http://<hostname>:9502/analytics` → **Console → Semantic Models**

2. Open the target Semantic Model.

3. Navigate to **Security** within the Semantic Model editor.

4. For each **Subject Area**, set permissions to:
   - **Read** access for `oas_anonymous` (or its assigned application role) only for subject areas required for anonymous dashboards.
   - **No Access** for all other subject areas.

5. Save and deploy the updated Semantic Model.

### 7.2 Using the Oracle BI Administration Tool (Offline/Legacy)

If the RPD is still managed using the desktop Oracle BI Administration Tool:

1. Open the **Oracle BI Administration Tool** and connect to the RPD online or open it offline.

2. Navigate to **Manage → Identity** and confirm `oas_anonymous` is visible.

3. For each **Subject Area**, right-click and select **Permissions**.

4. Explicitly set **Read** access for `oas_anonymous` (or its assigned application role) only for subject areas required for anonymous dashboards.

5. Set all other subject areas to **No Access** for the anonymous user.

6. Save and deploy the updated RPD.

### 7.3 Using Data Sets (OAS 8.0 Self-Service)

For self-service workbooks and datasets:
- Assign **Viewer** access only to datasets required for anonymous dashboards.
- Use **Workbook Sharing** to explicitly share workbooks with the `oas_anonymous` user or the `BIAnonymous` application role.

---

## Security Considerations

| Consideration | Recommendation |
|---|---|
| **Least Privilege** | Grant anonymous user only the minimum permissions (Read/Run) needed to access specific content |
| **No PII Exposure** | Ensure no personally identifiable information (PII) or sensitive financial data is included in anonymous dashboards |
| **Data Filters** | Apply row-level security / data filters on subject areas to limit the data visible to the anonymous user |
| **Audit Logging** | Enable OAS audit logging to track access by the anonymous user account |
| **Password Rotation** | Even though the password is not directly used by end users, rotate the `oas_anonymous` account password periodically |
| **Network Security** | Restrict anonymous access to internal networks or specific IP ranges using WebLogic connection filters or OCI network security groups if needed |
| **HTTPS** | Ensure OAS is served over HTTPS to protect data in transit even for anonymous content |
| **Session Timeout** | Configure appropriate session timeout for anonymous sessions using `<SessionTimeoutMinutes>` in `instanceconfig.xml` (OAS 8.0) |
| **OCI IAM Policies** | If using OCI IAM, define OCI policies to restrict the `oas_anonymous` user's access to OCI resources |
| **Rate Limiting** | Consider applying rate limiting or WAF rules to the anonymous access URLs to prevent abuse |

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

6. **Validate via OAS REST API (OAS 8.0):**
   - Use the OAS REST API to confirm the anonymous user's role assignment:
   ```bash
   curl -X GET \
     "http://<hostname>:9502/api/20210901/applicationRoles/BIConsumer/members" \
     -H "Accept: application/json" \
     -u <admin_user>:<admin_password>
   ```
   - Verify that `oas_anonymous` appears in the response.

---

## Troubleshooting

| Issue | Possible Cause | Resolution |
|---|---|---|
| Anonymous access redirects to login page | `instanceconfig.xml` not updated or not restarted | Verify the `<AnonymousUser>` section in `instanceconfig.xml` and restart Presentation Services |
| "Account does not exist" error | `oas_anonymous` user not created in identity store | Create the user in WebLogic Embedded LDAP, external directory, or OCI IAM (see Step 2) |
| "Insufficient privileges" error | Application role not assigned to `oas_anonymous` | Assign `BIConsumer` or a custom role to the anonymous user (see Step 3) |
| Catalog objects not visible | Folder/object permissions not set for `oas_anonymous` | Grant read permissions on catalog objects (see Step 4) |
| OAS shows login prompt even with correct URL | `<logon>true</logon>` not set in `instanceconfig.xml` | Update `instanceconfig.xml` and restart |
| Anonymous user can see restricted dashboards | Permissions applied too broadly | Review and restrict catalog permissions; verify data-level security filters |
| WebLogic blocking anonymous requests | Anonymous Authentication Provider not configured | Add and configure `AnonymousAuthenticator` in WebLogic (see Step 5) |
| Dashboard loads but data is missing | Subject area / Semantic Model permissions not granted | Grant read access to required subject areas in the Semantic Model or RPD (see Step 7) |
| OCI IAM authentication errors | OCI IAM federation misconfigured | Verify OCI IAM federation settings and that `oas_anonymous` user is correctly provisioned in OCI IAM |
| Self-service Workbook not accessible anonymously | Workbook not shared with `oas_anonymous` or `BIAnonymous` role | Share the Workbook with the anonymous user at the Viewer permission level (see Step 7.3) |
| Session timeout too short / too long | Default session timeout applies | Configure `<SessionTimeoutMinutes>` inside `<AnonymousUser>` in `instanceconfig.xml` (see Step 1.2) |

---

## References

- [Oracle Analytics Server 8.0 Documentation](https://docs.oracle.com/en/middleware/bi/analytics-server/index.html)
- [Oracle Analytics Server 8.0 Security Guide](https://docs.oracle.com/en/middleware/bi/analytics-server/user-guide-security/index.html)
- [Oracle Analytics Server 8.0 Administrator's Guide](https://docs.oracle.com/en/middleware/bi/analytics-server/administer/index.html)
- [Oracle Analytics Server 8.0 Semantic Modeler Guide](https://docs.oracle.com/en/middleware/bi/analytics-server/semantic-modeler/index.html)
- [WebLogic Server Security Providers Documentation](https://docs.oracle.com/en/middleware/fusion-middleware/weblogic-server/index.html)
- [OCI IAM Integration with Oracle Analytics Server](https://docs.oracle.com/en/cloud/paas/analytics-cloud/index.html)

---

*Document Version: 1.0 | Applicable OAS Version: 8.0 | Last Updated: March 2026*
