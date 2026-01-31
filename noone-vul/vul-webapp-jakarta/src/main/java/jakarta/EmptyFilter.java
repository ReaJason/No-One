package jakarta;

import jakarta.servlet.*;

import java.io.IOException;

/**
 * @author ReaJason
 * @since 2025/2/23
 */
public class EmptyFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        chain.doFilter(request, response);
    }
}
