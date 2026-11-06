import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";

import HomePage from "@/routes/home";

describe("HomePage project links", () => {
  it("opens the existing project-filtered shell list instead of an unregistered route", () => {
    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>,
    );

    expect(screen.getByRole("link", { name: /RASP VUL/ })).toHaveAttribute(
      "href",
      "/shells?projectId=1",
    );
    expect(screen.getByRole("link", { name: /Tomcat Server/ })).toHaveAttribute(
      "href",
      "/shells?projectId=2",
    );
  });
});
