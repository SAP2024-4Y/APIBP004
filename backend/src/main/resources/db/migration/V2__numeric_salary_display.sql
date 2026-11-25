-- Dataset contract: integer salaries are whole USD per year. No currency conversion.
-- Preserve legacy-only text: it is display-only until numeric bounds are supplied.
UPDATE jobs SET salary_range = CASE
    WHEN min_salary IS NOT NULL AND max_salary IS NOT NULL THEN CAST(min_salary AS VARCHAR) || ' - ' || CAST(max_salary AS VARCHAR)
    WHEN min_salary IS NOT NULL THEN CAST(min_salary AS VARCHAR) || '+'
    ELSE 'Up to ' || CAST(max_salary AS VARCHAR)
END WHERE min_salary IS NOT NULL OR max_salary IS NOT NULL;
