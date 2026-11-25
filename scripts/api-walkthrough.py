#!/usr/bin/env python3
"""Executable demo-data journey. Creates records; run only against a disposable demo API."""
import argparse
import json
import os
from pathlib import Path
import secrets
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

EXAMPLES = Path(__file__).resolve().parents[1] / "docs" / "examples"


def check(condition, message):
    if not condition:
        raise RuntimeError(message)


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.count = 0

    def call(self, method, path, payload=None, token=None, expected=200, code=None):
        headers = {"Accept": "application/json", "Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        encoded = None if payload is None else json.dumps(payload).encode()
        for attempt in range(3):
            request = urllib.request.Request(self.base + path, data=encoded, headers=headers, method=method)
            try:
                response = urllib.request.urlopen(request, timeout=30)
            except urllib.error.HTTPError as error:
                response = error
            with response:
                status = response.code
                data = response.read()
                retry = response.headers.get("Retry-After", "60")
            if status != 429:
                break
            check(attempt < 2, "Rate limit persisted after two retries")
            delay = min(60, max(1, int(retry)))
            print(f"Rate limited; waiting {delay}s before retrying this rejected request.", flush=True)
            time.sleep(delay)
        result = json.loads(data) if data else None
        check(status == expected, f"{method} {path}: expected {expected}, received {status}; code={result.get('code') if isinstance(result, dict) else None}")
        if code:
            check(result.get("code") == code, f"Expected error code {code}")
            check(result.get("status") == expected and result.get("path") == urllib.parse.urlsplit(path).path, "Error contract mismatch")
        self.count += 1
        print(f"PASS {method} {path} -> {status}" + (f" {code}" if code else ""), flush=True)
        return result

    def graph(self, filename, variables, token=None):
        result = self.call("POST", "/graphql/query", {"query": (EXAMPLES / filename).read_text(), "variables": variables}, token)
        check(not result.get("errors"), f"GraphQL operation {filename} failed")
        return result["data"]

    def wait(self, seconds):
        deadline = time.monotonic() + seconds
        while True:
            try:
                with urllib.request.urlopen(self.base + "/health", timeout=5) as response:
                    if response.status == 200:
                        return
            except (urllib.error.URLError, TimeoutError):
                pass
            check(time.monotonic() < deadline, "API did not become healthy before the deadline")
            time.sleep(2)


def run(api):
    suffix = uuid.uuid4().hex[:12]
    password = secrets.token_urlsafe(18)
    # The ADMIN is provisioned by demo seeding; public registration cannot create one.
    admin = api.call("POST", "/api/v1/auth/token", {
        "username": os.getenv("WALKTHROUGH_ADMIN_USERNAME", "demo"),
        "password": os.getenv("WALKTHROUGH_ADMIN_PASSWORD", "demo-password")})["token"]
    accounts = {}
    for role in ("COMPANY", "CANDIDATE"):
        email = f"walkthrough-{role.lower()}-{suffix}@example.test"
        registered = api.call("POST", "/api/v1/auth/register", {"email": email, "password": password, "role": role}, expected=201)
        logged_in = api.call("POST", "/api/v1/auth/login", {"email": email, "password": password})
        check(logged_in["userId"] == registered["userId"] and logged_in["role"] == role, "Registration/login identity mismatch")
        accounts[role] = logged_in
    recruiter, applicant = accounts["COMPANY"]["token"], accounts["CANDIDATE"]["token"]
    company_input = {"companyName": "Walkthrough Company " + suffix, "industry": "Technology", "address": "Synthetic demo address"}
    company = api.call("POST", "/api/v1/companies", company_input, recruiter, 201)
    candidate = api.call("POST", "/api/v1/candidates", {"fullName": "Walkthrough Candidate", "location": "San Francisco", "experience": "Synthetic profile"}, applicant)
    check(company["userId"] == accounts["COMPANY"]["userId"] and not company["approved"], "Company owner or initial approval mismatch")
    check(candidate["userId"] == accounts["CANDIDATE"]["userId"], "Candidate owner mismatch")
    approved = api.call("PUT", f"/api/v1/companies/{company['id']}", {**company_input, "approved": True}, admin)
    check(approved["approved"], "Admin approval was not persisted")
    api.call("GET", "/api/v1/users", token=admin)
    api.call("GET", "/api/v1/users", expected=401, code="UNAUTHORIZED")
    api.call("GET", "/api/v1/users", token=applicant, expected=403, code="FORBIDDEN")

    job_input = {"companyId": company["id"], "title": "Walkthrough Engineer " + suffix,
                 "description": "Build and maintain this synthetic recruitment demonstration.",
                 "category": "Technology", "status": "OPEN", "location": "San Francisco",
                 "skillsRequired": "Java, SQL", "employmentType": "FULL_TIME",
                 "minSalary": 90000, "maxSalary": 120000, "latitude": 37.7749, "longitude": -122.4194}
    api.call("POST", "/api/v1/jobs", job_input, applicant, 403, "FORBIDDEN")
    job = api.call("POST", "/api/v1/jobs", job_input, recruiter, 201)
    job_id = job["id"]
    check(job["salaryRange"] == "90000 - 120000", "Numeric salary display mismatch")
    api.call("GET", f"/api/v1/jobs/{job_id}")
    search = urllib.parse.urlencode({"title": job_input["title"], "skills": "Java", "min_price": 80000, "max_price": 130000})
    page = api.call("GET", "/api/v1/jobs?" + search)
    check([item["id"] for item in page["data"]] == [job_id], "Combined search did not return the created job")
    first = api.call("GET", "/api/v1/jobs?sort=id&order=asc&limit=1")
    check(first["has_more"] and first["next_cursor"], "Demo dataset should provide a second page")
    second = api.call("GET", "/api/v1/jobs?" + urllib.parse.urlencode({"sort": "id", "order": "asc", "limit": 1, "after": first["next_cursor"]}))
    check(first["data"][0]["id"] != second["data"][0]["id"], "Pagination repeated a job")
    nearby = api.graph("jobs-near.graphql", {"latitude": 37.7749, "longitude": -122.4194, "radiusKm": 5})["jobsNear"]
    check(str(job_id) in {item["id"] for item in nearby}, "5 km GraphQL query missed the created job")
    features = api.call("GET", "/api/v1/jobs/map?latitude=37.7749&longitude=-122.4194&radius_km=5")["features"]
    check({str(f["properties"]["id"]) for f in features} == {item["id"] for item in nearby}, "REST and GraphQL radius results differ")
    api.call("GET", "/api/v1/jobs/map?latitude=95&longitude=0&radius_km=5", expected=400, code="VALIDATION_ERROR")

    application = api.call("POST", "/api/v1/applications", {"jobId": job_id}, applicant, 201)
    check(application["candidateId"] == candidate["id"] and application["status"] == "SUBMITTED", "Submission identity or status mismatch")
    check(application["appliedAt"].endswith("Z"), "Submission timestamp is not UTC")
    api.call("POST", "/api/v1/applications", {"jobId": job_id}, applicant, 409, "DUPLICATE_RESOURCE")
    applications = api.call("GET", f"/api/v1/applications/job/{job_id}", token=recruiter)
    check(application["id"] in {a["id"] for a in applications}, "Recruiter cannot see the application")
    status_path = f"/api/v1/applications/{application['id']}/status"
    api.call("PUT", status_path, "HIRED", recruiter, 409, "APPLICATION_CONFLICT")
    api.call("PUT", status_path, "REVIEWED", applicant, 403, "FORBIDDEN")
    for status in ("REVIEWED", "SHORTLISTED"):
        updated = api.call("PUT", status_path, status, recruiter)
        check(updated["status"] == status, "Status transition failed")

    start, end = "2030-01-10T10:00:00Z", "2030-01-10T11:00:00Z"
    reservation = {"jobId": job_id, "startsAt": "2030-01-10T15:30:00+05:30", "endsAt": "2030-01-10T16:30:00+05:30"}
    slot = api.graph("create-slot.graphql", {"input": reservation}, applicant)["createSlot"]
    check(slot["candidateId"] == candidate["id"] and slot["startsAt"] == start and slot["endsAt"] == end, "Slot identity or UTC normalization mismatch")
    api.call("POST", "/api/v1/slots", reservation, applicant, 409, "SLOT_CONFLICT")
    # Equivalent public composite views: job + company + all slots in the same window.
    rest_job = api.call("GET", f"/api/v1/jobs/{job_id}")
    rest_company = api.call("GET", f"/api/v1/companies/{rest_job['companyId']}")
    rest_slots = api.call("GET", "/api/v1/slots?" + urllib.parse.urlencode({"starts_at": start, "ends_at": end}))
    composite = api.graph("job-and-calendar.graphql", {"id": str(job_id), "start": start, "end": end})
    check(composite["job"]["title"] == rest_job["title"] and composite["job"]["company"]["companyName"] == rest_company["companyName"], "REST/GraphQL job-company views differ")
    def project(item):
        return (str(item["id"]), item["jobId"], item["startsAt"], item["endsAt"], item["status"])
    check(sorted(map(project, rest_slots)) == sorted(map(project, composite["slots"])), "REST/GraphQL calendar views differ")
    api.call("DELETE", f"/api/v1/slots/{slot['id']}", token=applicant, expected=204)
    api.call("DELETE", f"/api/v1/applications/{application['id']}", token=applicant, expected=204)
    api.call("GET", f"/api/v1/jobs/{job_id + 100000000}", expected=404, code="RESOURCE_NOT_FOUND")
    print(f"PASS: {api.count} HTTP checks plus response assertions. Job {job_id}, company {company['id']}, and candidate {candidate['id']} remain for inspection. Tokens/passwords were not printed.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--wait-seconds", type=int, default=60)
    args = parser.parse_args()
    parsed = urllib.parse.urlparse(args.base_url)
    check(parsed.scheme in ("http", "https") and parsed.netloc and not parsed.username, "Supply an HTTP(S) API origin without embedded credentials")
    api = Api(args.base_url)
    api.wait(args.wait_seconds)
    run(api)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, urllib.error.URLError, TimeoutError, KeyError, ValueError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
