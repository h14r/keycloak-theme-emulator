<#-- Gemeinsames Layout aller HTML-Mails. Tabellen + Inline-Stile für Outlook/Gmail. -->
<#macro emailLayout>
<html lang="${locale.language}" dir="${(ltr)?then('ltr','rtl')}">
<body style="margin:0;padding:0;background:#f4f5f7;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f4f5f7;">
  <tr>
    <td align="center" style="padding:24px 12px;">
      <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%;background:#ffffff;border-top:4px solid #0f6e56;">
        <tr>
          <td style="padding:20px 28px 0 28px;font:700 18px/1.3 Arial,Helvetica,sans-serif;color:#0f6e56;">${realmName}</td>
        </tr>
        <tr>
          <td style="padding:12px 28px 28px 28px;font:15px/1.55 Arial,Helvetica,sans-serif;color:#1c2128;">
            <#nested>
          </td>
        </tr>
      </table>
      <p style="font:12px/1.4 Arial,Helvetica,sans-serif;color:#5d6673;margin:12px 0 0 0;">${msg("emailFooterBeispiel", realmName)}</p>
    </td>
  </tr>
</table>
</body>
</html>
</#macro>
