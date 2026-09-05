# Supabase Authentication & Deep Link Configuration

## Production & Staging Requirements

For self-service password recovery to redirect users back into the MemoStamp mobile applications (Android & iOS), the canonical deep link scheme must be allow-listed in the Supabase project configuration.

### Hosted Supabase Dashboard Configuration
1. Open the [Supabase Dashboard](https://app.supabase.com).
2. Select your MemoStamp project.
3. Navigate to **Authentication** > **URL Configuration**.
4. In the **Redirect URLs** section, add the following URL:
   - `memostamp://auth/recovery`
5. Click **Save**.

### Security & Privacy Considerations
- **No Secret Credentials**: Never store service keys, personal access tokens, or admin passwords in mobile client configuration.
- **Canonical Scheme**: The `memostamp://auth/recovery` deep link is shared across both Android and iOS targets. No platform-specific redirect URLs should be configured.
- **Fail-Closed Validation**: The mobile applications strictly validate that the incoming redirect matches scheme `memostamp`, host `auth`, path `/recovery`, and type `recovery`. Any unrecognized parameters or mismatching credentials are discarded immediately.
