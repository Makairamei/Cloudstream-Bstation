/**
 * Bstation Subtitle Proxy Worker
 * Converts Bstation JSON subtitles to WebVTT format
 * 
 * Usage: https://your-worker.workers.dev/?url=<bstation_json_subtitle_url>
 */

addEventListener('fetch', event => {
    event.respondWith(handleRequest(event.request))
})

async function handleRequest(request) {
    const url = new URL(request.url)
    const subtitleUrl = url.searchParams.get('url')

    // CORS headers
    const corsHeaders = {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type',
    }

    // Handle OPTIONS request
    if (request.method === 'OPTIONS') {
        return new Response(null, { headers: corsHeaders })
    }

    // Check if URL parameter exists
    if (!subtitleUrl) {
        return new Response('Missing "url" parameter. Usage: ?url=<bstation_json_subtitle_url>', {
            status: 400,
            headers: { ...corsHeaders, 'Content-Type': 'text/plain' }
        })
    }

    try {
        // Validate URL is from Bstation/Bilibili CDN
        const parsedUrl = new URL(subtitleUrl)
        if (!parsedUrl.hostname.includes('bstation') &&
            !parsedUrl.hostname.includes('bilibili') &&
            !parsedUrl.hostname.includes('akamaized') &&
            !parsedUrl.hostname.includes('bstarstatic')) {
            return new Response('Invalid subtitle URL. Only Bstation/Bilibili URLs are allowed.', {
                status: 403,
                headers: { ...corsHeaders, 'Content-Type': 'text/plain' }
            })
        }

        // Fetch the JSON subtitle
        const response = await fetch(subtitleUrl, {
            headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
                'Referer': 'https://www.bilibili.tv/'
            }
        })

        if (!response.ok) {
            return new Response(`Failed to fetch subtitle: ${response.status}`, {
                status: response.status,
                headers: { ...corsHeaders, 'Content-Type': 'text/plain' }
            })
        }

        const jsonData = await response.json()

        // Convert to WebVTT
        const vttContent = convertToVtt(jsonData)

        return new Response(vttContent, {
            status: 200,
            headers: {
                ...corsHeaders,
                'Content-Type': 'text/vtt; charset=utf-8',
                'Cache-Control': 'public, max-age=86400'
            }
        })

    } catch (error) {
        return new Response(`Error: ${error.message}`, {
            status: 500,
            headers: { ...corsHeaders, 'Content-Type': 'text/plain' }
        })
    }
}

function convertToVtt(jsonData) {
    const body = jsonData.body || []

    if (!body.length) {
        return 'WEBVTT\n\n'
    }

    let vtt = 'WEBVTT\n\n'

    body.forEach((entry, index) => {
        const from = formatVttTime(entry.from || 0)
        const to = formatVttTime(entry.to || 0)
        const content = entry.content || ''

        if (content.trim()) {
            vtt += `${index + 1}\n`
            vtt += `${from} --> ${to}\n`
            vtt += `${content}\n\n`
        }
    })

    return vtt
}

function formatVttTime(seconds) {
    const h = Math.floor(seconds / 3600)
    const m = Math.floor((seconds % 3600) / 60)
    const s = Math.floor(seconds % 60)
    const ms = Math.floor((seconds % 1) * 1000)

    return `${pad(h)}:${pad(m)}:${pad(s)}.${pad3(ms)}`
}

function pad(num) {
    return num.toString().padStart(2, '0')
}

function pad3(num) {
    return num.toString().padStart(3, '0')
}
