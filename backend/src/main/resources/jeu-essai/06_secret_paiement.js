const axios = require('axios');

const apiKey = "pk_live_51HfZqPAz9XcVbN0tR2s";

async function payer(montant) {
  const res = await axios.post('https://api.paiement.example/v1/charges', { montant }, {
    headers: { Authorization: `Bearer ${apiKey}` }
  });
  return res.data;
}

module.exports = { payer };
