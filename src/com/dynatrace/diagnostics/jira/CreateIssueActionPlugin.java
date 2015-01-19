
 /**
  * This template file was generated by dynaTrace client.
  * The dynaTrace community portal can be found here: http://community.dynatrace.com/
  * For information how to publish a plugin please visit http://community.dynatrace.com/plugins/contribute/
  **/ 

package com.dynatrace.diagnostics.jira;

import com.dynatrace.diagnostics.pdk.*;
import com.dynatrace.diagnostics.sdk.resources.BaseConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;


public class CreateIssueActionPlugin implements Action {
	private static final Logger log = Logger.getLogger(CreateIssueActionPlugin.class.getName());
	
	@Override
	public Status setup(ActionEnvironment env) throws Exception {
		return new Status(Status.StatusCode.Success);
	}
	
	@Override
	public Status execute(ActionEnvironment env) throws Exception {
		log.info("Executing JIRA - Create Issue Action Plugin");

		String jiraUrl = env.getConfigString("jira_url");
		String jiraUsername = env.getConfigString("jira_username");
		String jiraPassword = env.getConfigPassword("jira_password");
		String jiraProjectId = env.getConfigString("jira_projectId");
		String jiraIssueTypeId = env.getConfigString("jira_issueTypeId");
		String jiraComponentId = env.getConfigString("jira_componentId");
		String jiraPriorityId = env.getConfigString("jira_priorityId");
		String jiraReporter = env.getConfigString("jira_reporter");
		String jiraAssignee = env.getConfigString("jira_assignee");
		String jiraVersion = env.getConfigString("jira_version");
		
		if (jiraAssignee==null || jiraAssignee.isEmpty()) {
			// Default the assignee to the reporter
			jiraAssignee=jiraReporter;
		}
		
		if (jiraUrl==null || jiraUsername==null || jiraPassword==null || jiraProjectId==null || 
				jiraIssueTypeId==null || jiraReporter==null || jiraUrl.isEmpty() || 
				jiraUsername.isEmpty() || jiraPassword.isEmpty() || jiraProjectId.isEmpty() || 
				jiraIssueTypeId.isEmpty() || jiraReporter.isEmpty()) {
			log.severe("Required parameters to create issue in JIRA not present");
			// For easier testing the plugin will continue even without all its required parameters
			// return new Status(Status.StatusCode.ErrorInternalConfigurationProblem);
		}

		// Make sure URL always ends with the /
		if (!jiraUrl.endsWith("/")) {
			jiraUrl+="/";
		}
		
		log.info("Number of incidents to handle: "+env.getIncidents().size());
		
		for (Incident i : env.getIncidents()) {
			final StringBuilder desc = new StringBuilder();
		    appendln(desc, "Incident Details");
		    appendln(desc, "  Severity: ", getSeverityAsString(i));
		    appendln(desc, "  Start Time: ", i.getStartTime().toString());
		    appendln(desc, "  End Time: ", i.getEndTime().toString());
		    appendln(desc, "  Duration: ", i.getDuration().toString());
		    appendln(desc, "  Status: ", i.isClosed() ? "Closed" : "Open");
		    appendln(desc, "  Server: ", i.getServerName());
		
		    appendln(desc, "Incident Rule");
		    appendln(desc, "  Name: ", i.getIncidentRule().getName());
		    appendln(desc, "  Description: ", i.getIncidentRule().getDescription());
		    appendln(desc, "  Conditions: " + i.getIncidentRule().getCondition());	
		    
		    if (!i.getViolations().isEmpty()) {
			    appendln(desc, "Violations");
			    for (Violation v : i.getViolations()) {
			        appendln(desc, "  ", v.getViolatedMeasure().getName());
			        appendln(desc, "    Description: ", v.getViolatedMeasure().getDescription());
			        appendln(desc, "    Source: ", v.getViolatedMeasure().getSource().toString());
			        appendln(desc, "    Upper Severe: ", v.getViolatedMeasure().getUpperSevere().getValue().toString());
			        appendln(desc, "    Upper Warning: ", v.getViolatedMeasure().getUpperWarning().getValue().toString());
			        appendln(desc, "    Lower Warning: ", v.getViolatedMeasure().getLowerWarning().getValue().toString());
			        appendln(desc, "    Lower Severe: ", v.getViolatedMeasure().getLowerSevere().getValue().toString());
			        appendTriggerValues(v, desc);
			    }
			}

			if (!i.getPurePaths().isEmpty()) {
			    appendln(desc, "PurePaths");
			    for (PurePath path : i.getPurePaths()) {
			        appendln(desc, "- ", path.toString());
			    }
			}
			
			log.info("Description: "+desc.toString());
		
			if (jiraUrl!=null && !jiraUrl.isEmpty()) {
				try {
					return createJiraIssue(jiraUrl, jiraUsername, jiraPassword, jiraProjectId,
							jiraIssueTypeId, jiraComponentId, jiraPriorityId, jiraVersion, jiraReporter,
							jiraAssignee,i.getMessage(),desc.toString());
				}
				catch (IOException e) { 
					log.severe("Exception while raising issue in JIRA: "+e);
					return new Status(Status.StatusCode.ErrorInfrastructure);
				}
			}
		}
		return new Status(Status.StatusCode.Success);
	}
	@Override
	public void teardown(ActionEnvironment env) throws Exception {
	}
	
	private Status createJiraIssue(String jiraUrl, String jiraUsername, String jiraPassword, String jiraProjectId, 
			String jiraIssueTypeId, String jiraComponentId, String jiraPriorityId, String jiraVersion, 
			String jiraReporter, String jiraAssignee, String summary, String description) throws IOException {
		// Assemble JIRA URL
		final StringBuilder urlBuilder = new StringBuilder();
		urlBuilder.append(jiraUrl);
		urlBuilder.append("CreateIssueDetails.jspa?pid=");
		urlBuilder.append(jiraProjectId);
		urlBuilder.append("&issuetype=");
		urlBuilder.append(jiraIssueTypeId);
		if (jiraPriorityId!=null && !jiraPriorityId.isEmpty()) {
			urlBuilder.append("&priority=");
			urlBuilder.append(jiraPriorityId);
		}
		urlBuilder.append("&summary=");
		urlBuilder.append(URLEncoder.encode(summary, "UTF-8"));
		urlBuilder.append("&description=");
		urlBuilder.append(URLEncoder.encode(description, "UTF-8"));
		if (jiraVersion!=null && !jiraVersion.isEmpty()) {
			// Try to find the version ID that belongs to the given version string
			final Map<String, String> jiraVersions = getJIRAVersions(jiraUrl,jiraUsername,jiraPassword);
			final String versionID = jiraVersions.get(jiraVersion);
			if (versionID != null) {
				urlBuilder.append("&versions=");
				urlBuilder.append(URLEncoder.encode(versionID, "UTF-8"));
			} else {
				log.warning("Found no match for version '"+jiraVersion+"' in JIRA");
			}
		}
		urlBuilder.append("&reporter=");
		urlBuilder.append(jiraReporter);
		if (jiraComponentId!=null && !jiraComponentId.isEmpty()) {
			urlBuilder.append("&components=");
			urlBuilder.append(jiraComponentId);
		}
		urlBuilder.append("&os_username=");
		urlBuilder.append(jiraUsername);
		urlBuilder.append("&os_password=");
		urlBuilder.append(URLEncoder.encode(jiraPassword, "UTF-8"));

		// Open assembled JIRA URL
		final URL jiraURL = new URL(urlBuilder.toString());
		log.fine("Used JIRA URL is: "+jiraURL);
		
		final URLConnection connection = jiraURL.openConnection();
		if(connection instanceof HttpsURLConnection) {
			((HttpsURLConnection)connection).setSSLSocketFactory(createSSLSocketFactory());
			((HttpsURLConnection)connection).setHostnameVerifier(new EmptyHostnameVerifier());
		}
		connection.getContent();
		
		// Check response from JIRA to see if issue was created successfully
		final String targetURL = connection.getURL().toString();
		log.fine("Response URL from JIRA is: "+targetURL);
		if (targetURL.contains("/browse/")) {
			final String ticketNumber = targetURL.substring(targetURL.lastIndexOf('/') + 1);
			log.info("Successfully created JIRA issue with the following number: "+ticketNumber);
		}
		else {
			log.severe("Unknown problem while raising issue in JIRA");
			return new Status(Status.StatusCode.ErrorInfrastructure);
		}
		return new Status(Status.StatusCode.Success);
	}
	
	private String getSeverityAsString(Incident incident) {
		if(incident.getSeverity() != null) {
	    	switch(incident.getSeverity()) {
		    	case Error: return "Severe";
		    	case Informational: return "Informational";
		    	case Warning: return "Warning";
	    	}
		}
		return "";
	}
	
	private void appendln(StringBuilder buf, String... strings) {
	    for (String string : strings) {
	        buf.append(string);
	    }
	    buf.append("\n");
	}
		
	private void append(StringBuilder buf, String... strings) {
	    for (String string : strings) {
	        buf.append(string);
	    }
	}
		
	private void appendTriggerValues(Violation violation, StringBuilder sb) {
	    Collection<Violation.TriggerValue> triggerValues = violation.getTriggerValues();
	
	    if (!triggerValues.isEmpty()) {
	        sb.append("    Trigger Values:\n");
	        for (Violation.TriggerValue triggerValue : triggerValues) {
	        	append(sb,"      ",triggerValue.getSource().toString(),": ",triggerValue.getValue().toString());
	            double v = triggerValue.getValue().getValue();
	            double t = violation.getViolatedThreshold().getValue().getValue();
	            double dif = Math.abs(v - t);
	            if (dif != 0) {
	            	append(sb," (",BaseConstants.FORMAT_INTERNAL_DECIMAL.format(dif),violation.getViolatedMeasure().getUnit());
	    		    append(sb, v < t ? " below threshold" : " above threshold",")");
	            }
	        }
	        sb.append("\n");
	    }
	}	
	
	private static final String TD_NOWRAP_A_ID_VERSION = "<td nowrap><a id=\"version_";
	
	private static Map<String, String> getJIRAVersions(final String url, final String username,
			final String password) throws MalformedURLException,
			IOException {
		createSSLSocketFactory();
		final URLConnection connection = new URL(url+"browse/JLT?report=com.atlassian.jira.plugin.system.project:versions-panel&subset=-1&os_username=" +
				username + "&os_password=" + password).openConnection();
		if(connection instanceof HttpsURLConnection) {
			((HttpsURLConnection)connection).setSSLSocketFactory(createSSLSocketFactory());
			((HttpsURLConnection)connection).setHostnameVerifier(new EmptyHostnameVerifier());
		}
		final HashMap<String, String> versions = new HashMap<String, String>();
		final BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				final int index = line.indexOf(TD_NOWRAP_A_ID_VERSION);
				if (index != -1) {
					final String versionID = line.substring(index + TD_NOWRAP_A_ID_VERSION.length(), index +
							TD_NOWRAP_A_ID_VERSION.length() + 5);
					final int summaryIndex = line.indexOf('>', index + TD_NOWRAP_A_ID_VERSION.length());
					final int versionEndIndex = line.indexOf('<', summaryIndex);
					final String versionName = line.substring(summaryIndex + 1, versionEndIndex);
					versions.put(versionName, versionID);
				}
			}
		} finally {
			reader.close();
		}
		return versions;
	}	
	
	private static SSLSocketFactory createSSLSocketFactory() throws IOException {
		final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(final X509Certificate[] certs, final String authType) {
			}

			public void checkServerTrusted(final X509Certificate[] certs, final String authType) {
			}
		} };

		try {
			final SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			return sc.getSocketFactory();
		} catch (final KeyManagementException e) {
			throw new IOException(e);
		} catch (final NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}
	
	private static class EmptyHostnameVerifier implements HostnameVerifier {
		@Override
		public boolean verify(final String paramString, final SSLSession paramSSLSession) {
			return true;
		}
	}
}
