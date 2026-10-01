def test_application_import_smoke():
    from api.main import app
    assert app.title == "Medical Verifier"
    assert any(
        getattr(route, "path", None) == "/health"
        for route in app.routes
    )
